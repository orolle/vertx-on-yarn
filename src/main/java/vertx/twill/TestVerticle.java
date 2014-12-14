package vertx.twill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.platform.Verticle;


public class TestVerticle extends Verticle {
  static Logger LOG = LoggerFactory.getLogger(TestVerticle.class);

  @Override
  public void start() {
    super.start();
    final String id = container.config().getString("id", "<null>");
    
    vertx.setPeriodic(1000, new Handler<Long>() {
      public void handle(Long event) {
        LOG.info("Hello, this is verticle instance "+id+" on YARN!");
      }
    });
  }
}
