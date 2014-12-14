package vertx.twill;

import java.io.PrintWriter;
import java.io.StringWriter;


public class ExceptionUtil {

  public static String asString(Throwable e) {
    StringWriter errors = new StringWriter();
    e.printStackTrace(new PrintWriter(errors));
    return errors.toString();
  }
}
