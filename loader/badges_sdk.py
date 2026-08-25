# === BADGES SDK LOADER BEGIN ===
import base64
import lzma
import os
from typing import Any, Callable, Optional

from android.webkit import ValueCallback
from com.exteragram.messenger.plugins import PluginsController
from dalvik.system import InMemoryDexClassLoader
from java import dynamic_proxy
from java.lang import Class, String, System
from java.nio import ByteBuffer
from org.telegram.messenger import ApplicationLoader

BADGES_SDK_VERSION = "1.0.2"

_BADGES_SDK_ENGINE_CLASS = "ru.n08i40k.badges.Plugin"
_BADGES_SDK_MARK_KEY = "ru.n08i40k.badges.engine.loaded"
_BADGES_SDK_LEGACY_PLUGIN_ID = "badges-sdk"

_BADGES_SDK_DEX_BEGIN = "# === BADGES SDK DEX BEGIN ==="
_BADGES_SDK_DEX_END = "# === BADGES SDK DEX END ==="


class BadgesSdkStatus:
    """Чем закончилась попытка загрузки движка."""

    # движок загружен этим плагином
    LOADED = "loaded"

    # движок уже загружен другим плагином, версия совместима (такая же или выше)
    ALREADY_LOADED = "already_loaded"

    # уже загружена версия старее нашей: обновить её без перезапуска клиента
    # нельзя, наши бейджи будут работать по правилам старой версии
    OUTDATED = "outdated"

    # уже загружена версия с другим мажором (breaking change)
    INCOMPATIBLE = "incompatible"

    # движок не удалось загрузить, подробности в result.error
    FAILED = "failed"


class BadgesSdkLoadResult:
    __slots__ = ("status", "version", "loaded_by", "error")

    def __init__(
        self,
        status: str,
        version: Optional[str] = None,
        loaded_by: Optional[str] = None,
        error: Optional[BaseException] = None,
    ):
        self.status = status
        self.version = version
        self.loaded_by = loaded_by
        self.error = error

    @property
    def usable(self) -> bool:
        """Можно ли регистрировать свои бейджи."""

        return self.status in (BadgesSdkStatus.LOADED, BadgesSdkStatus.ALREADY_LOADED)

    @property
    def owned(self) -> bool:
        """Движок загружен именно этим лоадером."""

        return self.status == BadgesSdkStatus.LOADED

    def __repr__(self) -> str:
        return (
            f"BadgesSdkLoadResult(status={self.status}, version={self.version}, "
            f"loaded_by={self.loaded_by}, error={self.error})"
        )


class BadgesSdkLoader:
    """Загружает встроенный в этот файл DEX Badges SDK в память процесса."""

    def __init__(
        self,
        plugin_id: str,
        logger: Optional[Callable[[str], None]] = None,
        source_path: Optional[str] = None,
    ):
        self.plugin_id = plugin_id

        self._logger = logger
        self._source_path = source_path
        self._klass: Optional[Class] = None

    @property
    def loaded_engine(self) -> Optional[tuple[str, str]]:
        """(версия, id плагина-владельца) движка в процессе, если он загружен."""

        return self._read_mark()

    def load(self) -> BadgesSdkLoadResult:
        """Загрузить движок, если его ещё нет в процессе."""

        if self._klass is not None:
            return BadgesSdkLoadResult(
                BadgesSdkStatus.LOADED, BADGES_SDK_VERSION, self.plugin_id
            )

        previous = self._claim()

        if previous is not None:
            version, loaded_by = previous
            return BadgesSdkLoadResult(self._compare(version), version, loaded_by)

        try:
            self._klass = self._load_dex()
            self._inject()
        except BaseException as e:
            self._log_exception("Failed to load Badges SDK engine", e)
            self._klass = None
            self._release_claim()
            return BadgesSdkLoadResult(BadgesSdkStatus.FAILED, error=e)

        return BadgesSdkLoadResult(
            BadgesSdkStatus.LOADED, BADGES_SDK_VERSION, self.plugin_id
        )

    def remove_standalone_plugin(
        self, plugin_id: str = _BADGES_SDK_LEGACY_PLUGIN_ID
    ) -> bool:
        """Удалить у пользователя отдельный плагин Badges SDK."""

        try:
            controller = PluginsController.getInstance()

            if controller.getPlugins().get(plugin_id) is None:
                return False

            engine = controller.getPluginEngine(plugin_id)  # ty:ignore[invalid-argument-type]

            if engine is None:
                self._log(
                    f"Cannot remove the standalone '{plugin_id}' plugin: "
                    "its engine is unavailable"
                )
                return False

            engine.deletePlugin(plugin_id, None)
        except Exception as e:
            self._log_exception(
                f"Failed to remove the standalone '{plugin_id}' plugin", e
            )
            return False

        self._log(f"Removed the standalone '{plugin_id}' plugin")
        return True

    def unload(self) -> bool:
        """Выгрузить движок, если его загрузил этот плагин.

        В release-сборке движок остаётся в памяти до перезапуска клиента и метод возвращает False.
        """
        klass = self._klass

        if klass is None:
            return False

        self._klass = None
        ejected = False

        try:
            ejected = bool(self._call(klass, "eject"))
        except Exception as e:
            self._log_exception("Failed to eject Badges SDK engine", e)

        if ejected:
            self._release_claim()

        return ejected

    def _mark(self) -> str:
        return f"{BADGES_SDK_VERSION}|{self.plugin_id}"

    def _claim(self) -> Optional[tuple[str, str]]:
        try:
            previous = System.getProperties().putIfAbsent(
                String(_BADGES_SDK_MARK_KEY), String(self._mark())
            )
        except Exception as e:
            self._log_exception("Failed to claim Badges SDK engine slot", e)
            return None

        return None if previous is None else self._parse_mark(str(previous))

    def _release_claim(self):
        try:
            # снимаем только свою отметку
            System.getProperties().remove(
                String(_BADGES_SDK_MARK_KEY), String(self._mark())
            )
        except Exception as e:
            self._log_exception("Failed to release Badges SDK engine slot", e)

    def _read_mark(self) -> Optional[tuple[str, str]]:
        try:
            value = System.getProperty(String(_BADGES_SDK_MARK_KEY))
        except Exception:
            return None

        return None if value is None else self._parse_mark(str(value))

    @staticmethod
    def _parse_mark(value: str) -> tuple[str, str]:
        version, separator, loaded_by = value.partition("|")

        if not separator:
            return version, _BADGES_SDK_LEGACY_PLUGIN_ID

        return version, loaded_by

    @staticmethod
    def _parse_version(version: str) -> Optional[tuple[int, ...]]:
        parts = version.strip().split("-", 1)[0].split(".")

        try:
            return tuple(int(part) for part in parts[:3])
        except ValueError:
            return None

    @classmethod
    def _compare(cls, loaded_version: str) -> str:
        loaded = cls._parse_version(loaded_version)
        ours = cls._parse_version(BADGES_SDK_VERSION)

        if loaded is None or ours is None:
            return BadgesSdkStatus.INCOMPATIBLE

        if loaded[0] != ours[0] or (ours[0] == 0 and loaded[1] != ours[1]):
            return BadgesSdkStatus.INCOMPATIBLE

        if loaded >= ours:
            return BadgesSdkStatus.ALREADY_LOADED

        return BadgesSdkStatus.OUTDATED

    def _load_dex(self) -> Class:
        dex = self._read_embedded_dex()

        if dex is None:
            raise RuntimeError("embedded Badges SDK dex is missing or corrupted")

        loader = InMemoryDexClassLoader(
            ByteBuffer.wrap(dex),  # ty:ignore[invalid-argument-type]
            ApplicationLoader.applicationContext.getClassLoader(),
        )

        return loader.loadClass(String(_BADGES_SDK_ENGINE_CLASS))

    def _inject(self):
        klass = self._klass

        if klass is None:
            raise RuntimeError("Badges SDK engine class is not loaded")

        log = self._log

        try:
            log(
                f"Loading Badges SDK engine built at {self._call(klass, 'getBuildDate')}"
            )
        except Exception as e:
            self._log_exception("Failed to read Badges SDK build date", e)

        class _BadgesSdkLogger(dynamic_proxy(ValueCallback)):
            def onReceiveValue(self, arg0):
                log(str(arg0))

        self._call(
            klass,
            "inject",
            String(BADGES_SDK_VERSION),
            String(self.plugin_id),
            _BadgesSdkLogger(),
            types=(String.getClass(), String.getClass(), ValueCallback.getClass()),
        )

    @staticmethod
    def _call(klass: Class, name: str, *args: Any, types: tuple = ()) -> Any:
        return klass.getDeclaredMethod(String(name), *types).invoke(None, *args)

    def _read_embedded_dex(self) -> Optional[bytes]:
        source = self._read_own_source()

        if source is None:
            self._log("Failed to read plugin source with the embedded Badges SDK dex")
            return None

        decompressor = lzma.LZMADecompressor()
        payload = bytearray()
        collecting = False
        completed = False

        try:
            for line in source.splitlines():
                stripped = line.strip()

                if stripped == _BADGES_SDK_DEX_BEGIN:
                    collecting = True
                    continue

                if collecting and stripped == _BADGES_SDK_DEX_END:
                    completed = True
                    break

                if collecting and stripped.startswith("#"):
                    chunk = base64.b64decode(stripped[1:].strip())
                    payload += decompressor.decompress(chunk)
        except (EOFError, ValueError, lzma.LZMAError) as e:
            self._log_exception("Failed to decode the embedded Badges SDK dex", e)
            return None

        if completed and not decompressor.eof:
            self._log("Embedded Badges SDK dex payload is truncated")
            return None

        if not completed or not payload:
            self._log("Embedded Badges SDK dex payload is empty")
            return None

        return bytes(payload)

    def _read_own_source(self) -> Optional[str]:
        candidates: list[str] = []

        if self._source_path:
            candidates.append(self._source_path)

        own_file = globals().get("__file__")

        if isinstance(own_file, str) and own_file:
            candidates.append(own_file)

        plugins_dir_getter = globals().get("get_plugins_dir")

        if callable(plugins_dir_getter):
            try:
                candidates.append(
                    os.path.join(plugins_dir_getter(), f"{self.plugin_id}.py")
                )
            except Exception as e:
                self._log_exception("Failed to resolve the plugins directory", e)

        for path in candidates:
            try:
                with open(path, "r", encoding="utf-8") as f:
                    return f.read()
            except Exception as e:
                self._log_exception(f"Failed to read plugin source at {path}", e)

        return None

    def _log(self, message: str):
        logger = self._logger

        if logger is None:
            return

        try:
            logger(message)
        except Exception:
            pass

    def _log_exception(self, message: str, exception: BaseException):
        self._log(f"{message}: {exception!r}")


# === BADGES SDK DEX BEGIN ===
# === BADGES SDK DEX END ===
# === BADGES SDK LOADER END ===
