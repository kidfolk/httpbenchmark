package me.kidfolk.httpbenchmark.app;

/**
 * Created by wujinxu on 2/26/14.
 */
public class Data {

    static final String BASE = "http://i.imgur.com/";

    static final String EXT = ".jpg";

    static int[] sSquareWidthArray =
            {16, 48, 100, 145,
                    200, 250, 300, 350, 400, 460, 490, 540, 600, 640, 670};

    static final String URL = "http://gaitaobao4.alicdn.com/tfscom/T1dPh7FElcXXXXXXXX-700X1050.jpg";

    static final String[] URLS = new String[sSquareWidthArray.length];

    static {
        for (int i = 0; i < sSquareWidthArray.length; i++) {
            URLS[i] = URL + "_" + sSquareWidthArray[i] + "x" + sSquareWidthArray[i] + ".jpg";
        }
    }

    private Data() {
        // No instances.
    }

}
