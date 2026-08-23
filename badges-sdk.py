# fmt: off
from android.webkit import ValueCallback
from java import dynamic_proxy
from org.telegram.ui.ActionBar import AlertDialog
from client_utils import get_last_fragment
from ui.bulletin import BulletinHelper
from android_utils import run_on_ui_thread, copy_to_clipboard
import threading
import traceback
from android.content import DialogInterface
from android.os import Process
from android.util import Log
from base_plugin import BasePlugin
from org.telegram.messenger import ApplicationLoader, LocaleController
from java.nio import ByteBuffer
from dalvik.system import InMemoryDexClassLoader
import os
from java.lang import Class, String, System
from typing import Optional, Any, cast

__id__ = "badges-sdk"
__name__ = "Badges SDK"
__description__ = "Библиотека, позволяющая другим плагинам добавлять неограниченное количество бейджей пользователям.\n\nНе нарушает правила exteraGram, так так не вмешивается в работу существующих бейджей, а добавляет свою реализацию, не конфликтующую с бейджами самого exteraGram."
__author__ = "@n08i40k_extera"
__version__ = "1.0.1"
__icon__ = "HowDidYouDoThis/17"
__min_version__ = "12.1.1"

DEBUG_MODE = False
LOGCAT_TAG = __id__

JVM_PLUGIN_CLASS = "ru.n08i40k.badges.Plugin"

SETTING_LAST_LOADED_VERSION = "last_loaded_version"

# The engine is never ejected in release, so a second dex would leave two live
# instances hooked at once. A plugin reload gets a fresh Python module, so the
# marker has to live in the JVM system properties to survive it.
ENGINE_LOADED_PROPS_KEY = "ru.n08i40k.badges.engine.loaded"

DEX_COMMENT_BEGIN = "# === EMDEDDED DEX BEGIN ==="
DEX_COMMENT_END = "# === EMDEDDED DEX END ==="


I18N_DIALOG: dict[str, dict[str, str]] = {
    "dialog.load_crash.title": {
        "en": "Plugin failed to load",
        "ru": "Не удалось загрузить плагин",
    },
    "dialog.load_crash.message": {
        "en": "The plugin crashed at stage `{stage}`. The report is copied to the clipboard.",
        "ru": "Плагин упал на этапе `{stage}`. Отчёт скопирован в буфер обмена.",
    },
    "dialog.load_crash.ok": {
        "en": "OK",
        "ru": "ОК",
    },
    "dialog.update_restart.title": {
        "en": "Client restart required",
        "ru": "Требуется перезапуск клиента",
    },
    "dialog.update_restart.message": {
        "en": "Badges SDK was updated from {previous} to {current}. Restart the client to finish the update.",
        "ru": "Badges SDK обновлён с {previous} до {current}. Перезапустите клиент, чтобы завершить обновление.",
    },
    "dialog.update_restart.restart": {"en": "Restart", "ru": "Перезапустить"},
}

I18N_STATUS: dict[str, dict[str, str]] = {
    "status.error.dex.missing": {
        "en": "Plugin engine is missing from the source file",
        "ru": "Движок плагина отсутствует в файле плагина",
    },
    "status.error.update.restart_failed": {
        "en": "Couldn't restart client",
        "ru": "Не удалось перезапустить клиент",
    },
}

I18N_STRINGS: dict[str, dict[str, str]] = {
    **I18N_DIALOG,
    **I18N_STATUS,
}

# fmt: on


def _is_engine_loaded() -> bool:
    try:
        return System.getProperty(String(ENGINE_LOADED_PROPS_KEY)) is not None
    except Exception:
        return False


def _mark_engine_loaded():
    try:
        System.setProperty(String(ENGINE_LOADED_PROPS_KEY), String(__version__))
    except Exception:
        pass


class JvmPluginBridge:
    """Loads classes.dex embedded (as a hex comment) into this very .py file."""

    klass: Optional[Class]

    def __init__(self, plugin: "TemplatePlugin"):
        self.plugin = plugin
        self.klass = None

    def load(self):
        """Load the engine into a loader of our own, never into the host's.

        Grafting the dex into the host class loader turned out to be unreliable,
        so the plugin always gets its own InMemoryDexClassLoader: the classes
        stay ejectable and cannot clash with anything the host already holds.
        """
        if not DEBUG_MODE and _is_engine_loaded():
            self.plugin.log(
                "The engine is already loaded in this process: "
                "refusing to inject a second instance"
            )
            return

        host_loader = ApplicationLoader.applicationContext.getClassLoader()

        dex_data = self._read_embedded_dex()
        if dex_data is None:
            self.plugin.log("Embedded DEX is unavailable; plugin will not load")
            self.plugin._show_error(self.plugin._t("status.error.dex.missing"))
            return

        try:
            loader = InMemoryDexClassLoader(
                ByteBuffer.wrap(dex_data),  # ty:ignore[invalid-argument-type]
                host_loader,
            )

            self.klass = loader.loadClass(String(JVM_PLUGIN_CLASS))
            _mark_engine_loaded()
        except Exception as e:
            self.plugin.log_exception("Failed to load DEX", e)

    def call(self, name: str, *args: Any, types: tuple = ()) -> Any:
        if self.klass is None:
            raise RuntimeError(f"cannot call {name}: JVM plugin is not loaded")

        return self.klass.getDeclaredMethod(String(name), *types).invoke(None, *args)

    def _read_own_source(self) -> Optional[str]:
        candidates: list[str] = []

        own_file = globals().get("__file__")
        if isinstance(own_file, str) and own_file:
            candidates.append(own_file)

        plugins_dir_getter = globals().get("get_plugins_dir")
        if callable(plugins_dir_getter):
            try:
                candidates.append(os.path.join(plugins_dir_getter(), f"{__id__}.py"))
            except Exception as e:
                self.plugin.log_exception("Failed to resolve plugins directory", e)

        for path in candidates:
            try:
                with open(path, "r", encoding="utf-8") as f:
                    return f.read()
            except Exception as e:
                self.plugin.log_exception(f"Failed to read plugin source at {path}", e)

        return None

    def _read_embedded_dex(self) -> Optional[bytes]:
        source = self._read_own_source()
        if source is None:
            self.plugin.log("Failed to read plugin source for embedded DEX")
            return None

        collecting = False
        hex_parts: list[str] = []

        for line in source.splitlines():
            stripped = line.strip()

            if stripped == DEX_COMMENT_BEGIN:
                collecting = True
                continue

            if stripped == DEX_COMMENT_END:
                break

            if collecting and stripped.startswith("#"):
                hex_parts.append(stripped[1:].strip())

        hex_data = "".join(hex_parts)
        if not hex_data:
            self.plugin.log("Embedded DEX payload is empty")
            return None

        try:
            return bytes.fromhex(hex_data)
        except ValueError as e:
            self.plugin.log_exception("Failed to decode embedded DEX", e)
            return None


class TemplatePlugin(BasePlugin):
    _load_logging_active = False
    _load_log_buffer: list[str] = []

    jvm_plugin: JvmPluginBridge

    def log(self, message: Any):
        text = str(message)
        super().log(text)

        if self._load_logging_active:
            self._load_log_buffer.append(text)

        try:
            Log.i(cast("String", LOGCAT_TAG), cast("String", text))
        except Exception:
            pass

    def log_exception(self, message: str, exception: BaseException):
        self.log(f"{message}: {exception}")

        for chunk in traceback.format_exception(
            type(exception),
            exception,
            exception.__traceback__,
        ):
            for line in chunk.rstrip().splitlines():
                if line:
                    self.log(line)

    def _show_error(self, message: str):
        run_on_ui_thread(lambda: BulletinHelper.show_error(message))

    def _show_info(self, message: str):
        run_on_ui_thread(lambda: BulletinHelper.show_info(message))

    def _t(self, key: str, **kwargs: Any) -> str:
        values = I18N_STRINGS.get(key)
        if values is None:
            text = key
        else:
            text = values.get(self._get_app_language_code()) or values.get("en") or key

        try:
            return text.format(**kwargs)
        except Exception:
            return text

    def _get_app_language_code(self) -> str:
        raw = None

        try:
            info = LocaleController.getInstance().getCurrentLocaleInfo()
            if info.hasBaseLang():
                raw = info.baseLangCode
            else:
                raw = info.getLangCode() or info.shortName
        except Exception:
            pass

        if not raw:
            try:
                raw = LocaleController.getInstance().getCurrentLocale().getLanguage()
            except Exception:
                return "en"

        return str(raw).strip().lower().replace("-", "_").split("_", 1)[0] or "en"

    def _start_load_logging(self):
        self._load_log_buffer = []
        self._load_logging_active = True

    def _stop_load_logging(self):
        self._load_logging_active = False
        self._load_log_buffer = []

    def _handle_load_failure(self, stage: str, exception: BaseException):
        self.log_exception(f"Plugin load failed ({stage})", exception)

        logs = "\n".join(self._load_log_buffer)
        report = (
            f"Stage: `{stage}`\n"
            f"Plugin version: `{__version__}`\n\n"
            f"Error:\n```\n{exception}\n```\n\n"
            f"Log:\n```\n{logs}\n```"
        )

        try:
            copy_to_clipboard(report)
        except Exception as e:
            self.log_exception("Failed to copy load-crash report to clipboard", e)

        self._show_load_crash_dialog(stage)

    def _show_load_crash_dialog(self, stage: str):
        def show():
            try:
                fragment = get_last_fragment()
            except Exception:
                fragment = None

            message = self._t("dialog.load_crash.message", stage=stage)

            if fragment is None:
                self._show_error(message)
                return

            try:
                fragment.showDialog(
                    AlertDialog.Builder(fragment.getContext())
                    .setTitle(String(self._t("dialog.load_crash.title")))
                    .setMessage(String(message))
                    .setPositiveButton(
                        String(self._t("dialog.load_crash.ok")),
                        None,  # ty:ignore[invalid-argument-type]
                    )
                    .create()
                )
            except Exception as e:
                self.log_exception("Failed to show load crash dialog", e)
                self._show_error(message)

        run_on_ui_thread(show)

    def _get_last_loaded_version(self) -> str:
        try:
            return str(self.get_setting(SETTING_LAST_LOADED_VERSION, ""))
        except Exception as e:
            self.log_exception("Failed to read last loaded plugin version", e)
            return ""

    def _persist_current_loaded_version(self):
        try:
            self.set_setting(SETTING_LAST_LOADED_VERSION, __version__)
        except Exception as e:
            self.log_exception("Failed to persist last loaded plugin version", e)

    def _should_pause_load_for_update(self) -> bool:
        if DEBUG_MODE:
            return False

        previous_version = self._get_last_loaded_version()

        if len(previous_version) == 0 or previous_version == __version__:
            self._persist_current_loaded_version()
            return False

        self.log(
            f"Plugin was updated from {previous_version} to {__version__}: "
            "load paused until the client restarts"
        )
        self._show_update_restart_dialog(previous_version)
        return True

    def _show_update_restart_dialog(self, previous_version: str):
        def show():
            try:
                fragment = get_last_fragment()
            except Exception:
                fragment = None

            message = self._t(
                "dialog.update_restart.message",
                previous=previous_version,
                current=__version__,
            )

            if fragment is None:
                self.log("Update restart dialog deferred: UI context is unavailable")
                self._schedule_update_restart_dialog_retry(previous_version)
                return

            self_outer = self

            class RestartClickListener(
                dynamic_proxy(AlertDialog.OnButtonClickListener)
            ):
                def onClick(self, _dialog: AlertDialog, _which: int) -> None:
                    self_outer._persist_current_loaded_version()
                    self_outer._restart_client()

            class DismissListener(dynamic_proxy(DialogInterface.OnDismissListener)):
                def onDismiss(self, arg0) -> None:
                    self_outer._persist_current_loaded_version()
                    self_outer._restart_client()

            try:
                fragment.showDialog(
                    AlertDialog.Builder(fragment.getContext())
                    .setTitle(String(self._t("dialog.update_restart.title")))
                    .setMessage(String(message))
                    .setPositiveButton(
                        String(self._t("dialog.update_restart.restart")),
                        RestartClickListener(),
                    )
                    .setOnDismissListener(DismissListener())
                    .setOnPreDismissListener(DismissListener())
                    .create()
                )
            except Exception as e:
                self.log_exception("Failed to show update restart dialog", e)
                self._show_info(message)
                self._schedule_update_restart_dialog_retry(previous_version)

        run_on_ui_thread(show)

    def _schedule_update_restart_dialog_retry(self, previous_version: str):
        timer = threading.Timer(
            1.0,
            lambda: self._show_update_restart_dialog(previous_version),
        )
        timer.daemon = True
        timer.start()

    def _restart_client(self):
        def restart():
            try:
                Process.killProcess(Process.myPid())
            except Exception as e:
                self.log_exception("Failed to restart client", e)
                self._show_error(self._t("status.error.update.restart_failed"))

        run_on_ui_thread(restart)

    def _prepare_jvm_plugin(self) -> bool:
        self.jvm_plugin = JvmPluginBridge(self)
        self.jvm_plugin.load()

        return self.jvm_plugin.klass is not None

    def _inject_jvm_plugin(self):
        try:
            self.log(f"Loading JVM plugin {self.jvm_plugin.call('getBuildDate')}")
        except Exception as e:
            self.log_exception("Failed to infer JVM plugin version", e)

        ref = self

        class Logger(dynamic_proxy(ValueCallback)):
            def onReceiveValue(self, arg0):
                ref.log(str(arg0))

        self.jvm_plugin.call(
            "inject",
            String(__version__),
            Logger(),
            types=(String.getClass(), ValueCallback.getClass()),
        )
        self.log("JVM plugin injected successfully")

    def on_plugin_load(self):
        self._start_load_logging()

        if self._should_pause_load_for_update():
            return

        if not self._prepare_jvm_plugin():
            return

        try:
            self._inject_jvm_plugin()
        except BaseException as e:
            self._handle_load_failure("inject", e)
            self.jvm_plugin.klass = None
            return

        self._stop_load_logging()

    def on_plugin_unload(self):
        jvm_plugin = getattr(self, "jvm_plugin", None)

        if jvm_plugin is None or jvm_plugin.klass is None:
            return

        try:
            jvm_plugin.call("eject")
            self.log("JVM plugin ejected successfully")
        except Exception as e:
            self.log_exception("Failed to eject JVM plugin", e)

        jvm_plugin.klass = None


# === EMDEDDED DEX BEGIN ===
# === EMDEDDED DEX END ===
