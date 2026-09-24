package ru.n08i40k.badges.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public interface BadgesSdk {
    /**
     * Parameters of {@link #rebindViews(RebindViewsParams)}.
     * <p>
     * Instances are immutable and created through {@link #builder(ViewFactory)}.
     */
    final class RebindViewsParams {
        @NotNull
        private final ViewFactory viewFactory;

        @Nullable
        private final Long userId;

        private RebindViewsParams(@NotNull Builder b) {
            this.viewFactory = b.viewFactory;
            this.userId = b.userId;
        }

        /**
         * Create a builder.
         *
         * @param viewFactory factory whose views must be rebound; must be installed with
         *                    {@link #installViewFactory(ViewFactory)}
         *
         * @return new builder
         *
         * @throws NullPointerException if {@code viewFactory} is {@code null}
         */
        public static @NotNull Builder builder(@NotNull ViewFactory viewFactory) {
            return new Builder(viewFactory);
        }

        /**
         * Get the factory whose views must be rebound.
         *
         * @return factory, never {@code null}
         */
        public @NotNull ViewFactory getViewFactory() {
            return this.viewFactory;
        }

        /**
         * Get the user whose views must be rebound.
         *
         * @return user id, or {@code null} to rebind views of every user
         */
        public @Nullable Long getUserId() {
            return this.userId;
        }

        /**
         * Builder of {@link RebindViewsParams}.
         */
        public static final class Builder {
            @NotNull
            private final ViewFactory viewFactory;

            @Nullable
            private Long userId = null;

            private Builder(@NotNull ViewFactory viewFactory) {
                this.viewFactory = Objects.requireNonNull(viewFactory, "viewFactory");
            }

            /**
             * Limit the rebind to views shown for a single user.
             * <p>
             * When not set, views of every user are rebound.
             *
             * @param userId Telegram user id
             *
             * @return this builder
             */
            public @NotNull Builder userId(long userId) {
                this.userId = userId;
                return this;
            }

            /**
             * Build the parameters.
             *
             * @return immutable parameters
             */
            public @NotNull RebindViewsParams build() {
                return new RebindViewsParams(this);
            }
        }
    }


    @NotNull String getVersion();

    /**
     * Install a badge factory.
     * <p>
     * Every badge view shown by the engine is recreated: existing views are passed to
     * {@link ViewFactory#destroy(android.view.View)} of their factories, then each installed
     * factory creates a new view. Factories are drawn in installation order.
     * <p>
     * Factories are bound to the plugin that installed them and are uninstalled automatically when
     * that plugin is unloaded.
     *
     * @param viewFactory factory to install
     *
     * @throws IllegalArgumentException if this factory is already installed by the same plugin
     */
    void installViewFactory(@NotNull ViewFactory viewFactory);

    /**
     * Uninstall a badge factory.
     * <p>
     * Views created by the factory are passed to {@link ViewFactory#destroy(android.view.View)},
     * and badge views of the remaining factories are recreated. Does nothing if the factory is not
     * installed.
     *
     * @param viewFactory factory to uninstall; compared with {@link Object#equals(Object)}
     */
    void uninstallViewFactory(@NotNull ViewFactory viewFactory);

    /**
     * Call {@link ViewFactory#bind(ViewFactory.BindParams)} again on views created by a factory.
     * <p>
     * Use it when data behind a badge has changed. Views are reused, not recreated. If a badge
     * changes its width, the affected dialog list cells are re-measured.
     *
     * @param params factory and optional user to rebind
     */
    void rebindViews(@NotNull RebindViewsParams params);
}