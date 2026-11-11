package dev.madusha.shangos;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ResumeTest {
    private static final long MIN = 60_000L;

    @Test
    public void continueLineHasTitleEpisodeAndTimeLeft() {
        assertEquals(
                "Continue: Stranger Things · S4 E3 · 18 min left",
                Resume.line("Stranger Things", "4", "3", 42 * MIN, 60 * MIN, Resume.CONTINUE));
    }

    @Test
    public void aFilmWithoutEpisodesShowsHoursLeft() {
        assertEquals(
                "Continue: Dune · 1 h 5 min left",
                Resume.line("Dune", null, null, 77 * MIN, 142 * MIN, Resume.CONTINUE));
        assertEquals("1 h left", Resume.left(60 * MIN));
    }

    @Test
    public void progressStartedWinsOverTheListType() {
        assertEquals(
                "Continue: Show · S1 E2 · 10 min left",
                Resume.line("Show", "1", "2", 20 * MIN, 30 * MIN, Resume.NEXT));
    }

    @Test
    public void withoutProgressTheTypeSaysWhy() {
        assertEquals("Next episode: Show · S1 E2", Resume.line("Show", "1", "2", 0, 0, Resume.NEXT));
        assertEquals("New: Show", Resume.line("Show", null, null, 0, 0, Resume.NEW));
        assertEquals("Watchlist: Show", Resume.line("Show", null, null, 0, 0, Resume.WATCHLIST));
        assertEquals("Continue: Show", Resume.line("Show", null, null, 0, 0, Resume.CONTINUE));
    }

    @Test
    public void noTitleMeansNoLine() {
        assertEquals("", Resume.line(null, "1", "2", MIN, 2 * MIN, Resume.CONTINUE));
        assertEquals("", Resume.line("", null, null, 0, 0, Resume.NEW));
    }

    @Test
    public void episodeLabelsAndProgress() {
        assertEquals("S4 E3", Resume.episode("4", "3"));
        assertEquals("Episode 3", Resume.episode(null, "3"));
        assertEquals("", Resume.episode("4", null));
        assertEquals(-1f, Resume.progress(0, 100), 0f);
        assertEquals(.5f, Resume.progress(50, 100), 0f);
        assertEquals(.03f, Resume.progress(1, 1000), 0f);
        assertEquals("a · b", Resume.join("a", "b"));
        assertEquals("b", Resume.join("", "b"));
        assertEquals("a", Resume.join("a", null));
    }
}
