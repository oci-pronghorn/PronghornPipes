package com.ociweb.pronghorn.util;

public interface PHAppendable extends Appendable {
    Appendable append(CharSequence csq);

    Appendable append(CharSequence csq, int start, int end);

    Appendable append(char c);
}