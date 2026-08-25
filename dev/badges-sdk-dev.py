from base_plugin import BasePlugin

__id__ = "badges-sdk-dev"
__name__ = "Badges SDK (dev)"
__description__ = "Загружает движок Badges SDK для отладки."
__author__ = "@n08i40k_extera"
__version__ = "1.0.0"
__icon__ = "HowDidYouDoThis/17"
__min_version__ = "12.1.1"


# === BADGES SDK LOADER BEGIN ===
# === BADGES SDK LOADER END ===


class BadgesSdkDevPlugin(BasePlugin):
    def on_plugin_load(self):
        self.badges = BadgesSdkLoader(  # noqa: F821  # ty:ignore[unresolved-reference]
            __id__, logger=self.log
        )
        self.log(f"Badges SDK: {self.badges.load()}")

    def on_plugin_unload(self):
        badges = getattr(self, "badges", None)

        if badges is not None:
            self.log(f"Badges SDK ejected: {badges.unload()}")
