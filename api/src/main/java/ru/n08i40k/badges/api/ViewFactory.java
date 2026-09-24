package ru.n08i40k.badges.api;

import android.view.View;

import org.jetbrains.annotations.NotNull;

public interface ViewFactory {
    interface CreateParams {
        @NotNull View getParent();

        int getHeightInPx();
    }

    interface BindParams {
        @NotNull View getView();

        @NotNull Long getUserId();
    }

    /**
     * Should return id of plugin that owns this view factory.
     *
     * @return plugin id
     */
    @NotNull String getPluginId();

    /**
     * Creates view that will bee shows after.
     *
     * @param params create parameters
     *
     * @return unique view instance
     */
    @NotNull View create(@NotNull CreateParams params);

    /**
     * Binds created view to user.
     *
     * @param params bind parameters
     *
     * @return true if created view can be shown, false otherwise
     */
    boolean bind(@NotNull BindParams params);

    /**
     * Called before view will be destroyed, can be used to clean up.
     *
     * @param view view created earlier
     */
    void destroy(@NotNull View view);
}
