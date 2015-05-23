import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author Angel Nunez
 * @date 5/21/15
 */
public class Global extends GlobalSettings {

    @Override
    public void onStart(Application application) {
        Logger.info("Application has started");
        super.onStart(application);

        FiniteDuration initialDelay = Duration.create(30, TimeUnit.SECONDS);
        FiniteDuration interval = Duration.create(1, TimeUnit.HOURS);

        Akka.system().scheduler().schedule(initialDelay, interval, () -> {
            Logger.info("Running process " + new Date());
            controllers.Application.process();

        }, Akka.system().dispatcher());
    }
}
