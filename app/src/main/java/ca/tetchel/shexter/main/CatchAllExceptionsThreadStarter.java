package ca.tetchel.shexter.main;

import android.content.Context;

import ca.tetchel.shexter.eventlogger.EventLogger;

public class CatchAllExceptionsThreadStarter {


    public static void start(final Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                paramThrowable.printStackTrace();
                EventLogger.logError(context, paramThrowable);

                //Catch your exception
                // Without System.exit() this will not work.
                System.exit(2);
            }
        });
    }

}
