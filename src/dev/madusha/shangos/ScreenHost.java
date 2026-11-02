package dev.madusha.shangos;

/**
 * What a full-screen panel (Privacy, the widget editor) needs from the launcher it is drawn over.
 */
interface ScreenHost {
    boolean motion();

    /** The sound of choosing something. */
    void click();

    /** The sound of moving to row {@code index}. */
    void step(int index);

    void post(Runnable action);

    /** Back was pressed: show Settings again with row {@code row} selected. */
    void backToSettings(int row);
}
