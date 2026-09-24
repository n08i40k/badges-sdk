package ru.n08i40k.badges.hook


internal abstract class HookBundle {
    abstract fun inject(before: InstallHook, after: InstallHook)
}