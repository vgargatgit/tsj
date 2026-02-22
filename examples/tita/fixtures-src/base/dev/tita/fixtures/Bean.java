package dev.tita.fixtures;

public final class Bean {
    private String title;
    private boolean ready;

    public String getTitle() {
        return title;
    }

    public void setTitle(final String nextTitle) {
        this.title = nextTitle;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(final boolean nextReady) {
        this.ready = nextReady;
    }

    public String getURL() {
        return "URL";
    }

    public String getUrl() {
        return "url";
    }

    public void setValue(final int value) {
        // no-op
    }

    public void setValue(final String value) {
        // no-op
    }
}
