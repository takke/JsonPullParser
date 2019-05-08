package net.vvakame.util.jsonpullparser;

import java.io.IOException;
import java.io.Reader;

class SimpleStringReader {

    private final String in;

    private int pos = 0;

    private int lastMark = 0;

    public SimpleStringReader(String input) {
        in = input;
    }

    public SimpleStringReader(Reader input) {
        try {
            StringBuilder s = new StringBuilder();
            char[] readBuf = new char[102400];
            int n = input.read(readBuf);
            while (n >= 0) {
                s.append(readBuf, 0, n);
                n = input.read(readBuf);
            }
            in = s.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void mark(int readAheadLimit) {
        this.lastMark = pos;
    }

    public char read() {
        return in.charAt(pos++);
    }

    public void reset() {
        pos = lastMark;
    }
}
