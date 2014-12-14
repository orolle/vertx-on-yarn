package vertx.twill;


import java.io.PrintWriter;
import java.net.URL;

import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.twill.api.AbstractTwillRunnable;
import org.apache.twill.api.ResourceSpecification;
import org.apache.twill.api.ResourceSpecification.SizeUnit;
import org.apache.twill.api.TwillApplication;
import org.apache.twill.api.TwillController;
import org.apache.twill.api.TwillRunnerService;
import org.apache.twill.api.TwillSpecification;
import org.apache.twill.api.logging.PrinterLogHandler;
import org.apache.twill.common.Services;
import org.apache.twill.yarn.YarnTwillRunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.PlatformLocator;
import org.vertx.java.platform.PlatformManager;
import org.vertx.java.spi.cluster.impl.hazelcast.HazelcastClusterManagerFactory;


public class VertxTwillContainer {
  static String BOOTSTRAP_HOST = "0.0.0.0";
  static Logger LOG = LoggerFactory.getLogger(VertxTwillContainer.class);

  static class VertxApplication implements TwillApplication {

    public TwillSpecification configure() {
      return TwillSpecification.Builder.with()
          .setName("Vertx")
          .withRunnable()
          .add("worker", new VertxRunnable(), ResourceSpecification.Builder.with()
              .setVirtualCores(2)
              .setMemory(128, SizeUnit.MEGA)
              .setInstances(2)
              .build())
              .noLocalFiles()
              .withOrder()
              .begin("worker")
              .build()
              ;
    }

  }

  static class VertxRunnable extends AbstractTwillRunnable {
    private boolean running = true;
    private boolean stopped = false;

    @Override
    public void stop() {
      super.stop();
      this.destroy();
    }

    @Override
    public void destroy() {
      super.destroy();
      running = false;

      LOG.info("Destroy instance "+getContext().getRunId()+":"+getContext().getInstanceId());
    }

    /**
     * 
     */
    public void run() {
      try {
        new HazelcastClusterManagerFactory(); // workaround
        System.setProperty("vertx.clusterManagerFactory", "org.vertx.java.spi.cluster.impl.hazelcast.HazelcastClusterManagerFactory");
        LOG.info("Vertx on instance "+getContext().getRunId()+":"+getContext().getInstanceId());
        final PlatformManager pm = PlatformLocator.factory.createPlatformManager(0, BOOTSTRAP_HOST);

        doDeploySSH(pm, new Handler<Void>() {
          public void handle(Void event) {
            doDeployJs(pm, new Handler<Void> () {
              public void handle(Void event) {
                LOG.info("started ssh & java script");
              }
            });
          }
        });

        while(running) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            LOG.error(ExceptionUtil.asString(e));
          }
        }

        pm.undeployAll(new Handler<AsyncResult<Void>>() {
          public void handle(AsyncResult<Void> event) {
            pm.stop();
            stopped = true; 
          }
        });

        while(!stopped) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            LOG.error(ExceptionUtil.asString(e));
          }
        }
      }catch(Exception e) {
        LOG.error(ExceptionUtil.asString(e));
      }
    }

    /**
     * Crashub SSH shell to interact with vertx platform instances within
     * twills Hadoop YARN container instance.
     * @param pm
     * @param handler
     */
    private void doDeploySSH(PlatformManager pm, final Handler<Void> handler) {
      JsonObject shell_conf = new JsonObject()
      .putString("crash.auth", "simple")
      .putString("crash.auth.simple.username", "admin")
      .putString("crash.auth.simple.password", "admin")
      .putNumber("crash.ssh.port", 31999 - getContext().getInstanceId());

      pm.deployModule("org.crashub~vertx.shell~2.1.0", shell_conf, 1, new Handler<AsyncResult<String>>() {
        public void handle(AsyncResult<String> event) {
          if (event.succeeded()) {
            LOG.info(event.result());
            LOG.info("SSH on instance "+getContext().getRunId()+":"+getContext().getInstanceId()+" listen on " 
                + getContext().getHost().getHostAddress()+":" + (31999-getContext().getInstanceId()));
          } else {
            LOG.error(ExceptionUtil.asString(event.cause()));
          }
          
          handler.handle(null);
        }
      });
    }

    /**
     * js verticle
     * Proofs that java script runs on Hadoop YARN 
     * This is a unique and very impressive feature for the big data and node.js hype! 
     * 
     * @param pm
     * @param handler
     */
    private void doDeployJs(PlatformManager pm, final Handler<Void> handler) {
      JsonObject js_conf = new JsonObject()
      .putString("Hello", "World!");

      pm.deployVerticle("app.js", js_conf, new URL[] {}, 1, null, new Handler<AsyncResult<String>>() {
        public void handle(AsyncResult<String> event) {
          if (event.succeeded()) {
            LOG.info(event.result());
            LOG.info("Java script on instance "+getContext().getRunId()+":"+getContext().getInstanceId());
          } else {
            LOG.error(ExceptionUtil.asString(event.cause()));
          }
          handler.handle(null);
        }
      });
    }
  };

  /**
   * Das ist die Main Methode
   * 
   * @param args
   *   die Parameter für das Programm
   *   
   * @throws Exception
   *  Fehler wird geworfen wenn was schief läuft
   */
  public static void main(String[] args) throws Exception {
    YarnConfiguration conf = new YarnConfiguration();
    TwillRunnerService runner = new YarnTwillRunnerService(conf, "127.0.0.1:2181");
    runner.startAndWait();
    TwillController controller = runner
        .prepare(new VertxApplication())
        .start();
    controller.addLogHandler(new PrinterLogHandler(new PrintWriter(System.out)));
    System.out.println("worker = 1");

    /* 
    //Scale up
    Thread.sleep(60000);
    System.out.println("worker = 2");
    controller.changeInstances("worker", 2);
     */


    // Scale Down
    /*
    Thread.sleep(60000);
    System.out.println("worker = 1");
    controller.changeInstances("worker", 1);
     */
    Services.getCompletionFuture(controller).get();
  }

}
