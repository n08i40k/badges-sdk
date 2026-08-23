package ru.n08i40k.badges.hook.impl

import com.exteragram.messenger.plugins.PythonPluginsEngine
import org.telegram.ui.LaunchActivity
import ru.n08i40k.badges.BadgesSdkService
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.Logger

class PluginUnloadHook : HookBundle() {
    override fun inject(before: InstallHook, after: InstallHook) {
        before(
            PythonPluginsEngine::class.java.getDeclaredMethod(
                "unloadPlugin",
                String::class.java
            )
        ) { params->
            BadgesSdkService.onPluginUnload(params.args[0] as? String ?: return@before)
        }
    }
}
