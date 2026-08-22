package ru.n08i40k.badges.hook


abstract class HookBundle {
    abstract fun inject(before: InstallHook, after: InstallHook)
}