package org.skywalking.apm.ui;

import java.util.concurrent.ThreadLocalRandom;

public class Main {

    public static void main(String[] args) {
        for (int i = 0; i < 300; i++) {
            if (i % 20 == 0)
                System.out.println();
            System.out.print(ThreadLocalRandom.current().nextInt(500, 4000) + ",");
        }
    }
}
