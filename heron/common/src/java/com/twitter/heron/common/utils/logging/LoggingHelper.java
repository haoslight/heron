package com.twitter.heron.common.utils.logging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.PrintStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * A helper class to init corresponding LOGGER setting
 * Credits: https://blogs.oracle.com/nickstephen/entry/java_redirecting_system_out_and
 */
public class LoggingHelper {
  static public void loggerInit(Level level, boolean isRedirectStdOutErr) throws IOException {
    // Configure the root logger and its handlers so that all the
    // derived loggers will inherit the properties
    Logger rootLogger = Logger.getLogger("");
    for(Handler handler: rootLogger.getHandlers()) {
      handler.setLevel(level);
    }

    rootLogger.setLevel(level);

    if (isRedirectStdOutErr) {

      // Remove ConsoleHandler if present, to avoid StackOverflowError.
      // ConsoleHandler writes to System.err and since we are redirecting
      // System.err to Logger, it results in an infinte loop.
      for(Handler handler: rootLogger.getHandlers()) {
        if(handler instanceof ConsoleHandler) {
          rootLogger.removeHandler(handler);
        }
      }

      // now rebind stdout/stderr to logger
      Logger logger;
      LoggingOutputStream los;

      logger = Logger.getLogger("stdout");
      los = new LoggingOutputStream(logger, StdOutErrLevel.STDOUT);
      System.setOut(new PrintStream(los, true));

      logger = Logger.getLogger("stderr");
      los = new LoggingOutputStream(logger, StdOutErrLevel.STDERR);
      System.setErr(new PrintStream(los, true));
    }
  }

  static public void addLoggingHandler(Handler handler) {
    Logger.getLogger("").addHandler(handler);
  }

  /**
   * Initialize a <tt>FileHandler</tt> to write to a set of files
   * with optional append.  When (approximately) the given limit has
   * been written to one file, another file will be opened.  The
   * output will cycle through a set of count files.
   * The pattern of file name should be: ${processId}.log.index
   * <p/>
   * The <tt>FileHandler</tt> is configured based on <tt>LogManager</tt>
   * properties (or their default values) except that the given pattern
   * argument is used as the filename pattern, the file limit is
   * set to the limit argument, and the file count is set to the
   * given count argument, and the append mode is set to the given
   * <tt>append</tt> argument.
   * <p/>
   * The count must be at least 1.
   *
   * @param limit the maximum number of bytes to write to any one file
   * @param count the number of files to use
   * @param append specifies append mode
   * @throws IOException if there are IO problems opening the files.
   * @throws SecurityException if a security manager exists and if
   * the caller does not have <tt>LoggingPermission("control")</tt>.
   * @throws IllegalArgumentException if {@code limit < 0}, or {@code count < 1}.
   * @throws IllegalArgumentException if pattern is an empty string
   */
  static public FileHandler getFileHandler(String processId,
                                           String loggingDir,
                                           boolean append,
                                           int limit,
                                           int count) throws IOException, SecurityException {

    String pattern = loggingDir + "/" + processId + ".log.%g";


    FileHandler fileHandler = new FileHandler(pattern, limit, count, append);
    fileHandler.setFormatter(new SimpleFormatter());
    fileHandler.setEncoding("UTF-8");

    return fileHandler;
  }

  public static class StdOutErrLevel extends Level {
    /**
     * Private constructor
     */
    private StdOutErrLevel(String name, int value) {
      super(name, value);
    }

    /**
     * Level for STDOUT activity.
     */
    public static Level STDOUT =
        new StdOutErrLevel("STDOUT", Level.INFO.intValue() + 53);
    /**
     * Level for STDERR activity
     */
    public static Level STDERR =
        new StdOutErrLevel("STDERR", Level.INFO.intValue() + 54);

    /**
     * Method to avoid creating duplicate instances when deserializing the
     * object.
     *
     * @return the singleton instance of this <code>Level</code> value in this
     * classloader
     * @throws java.io.ObjectStreamException If unable to deserialize
     */
    protected Object readResolve()
        throws ObjectStreamException {
      if (this.intValue() == STDOUT.intValue())
        return STDOUT;
      if (this.intValue() == STDERR.intValue())
        return STDERR;
      throw new InvalidObjectException("Unknown instance :" + this);
    }
  }

  /**
   * An OutputStream that writes contents to a Logger upon each call to flush()
   */
  public static class LoggingOutputStream extends ByteArrayOutputStream {

    private String lineSeparator;

    private Logger logger;
    private Level level;

    /**
     * Constructor
     *
     * @param logger Logger to write to
     * @param level Level at which to write the log message
     */
    public LoggingOutputStream(Logger logger, Level level) {
      super();
      this.logger = logger;
      this.level = level;
      lineSeparator = System.getProperty("line.separator");
    }

    /**
     * upon flush() write the existing contents of the OutputStream
     * to the logger as a log record.
     *
     * @throws java.io.IOException in case of error
     */
    public void flush() throws IOException {

      String record;
      synchronized (this) {
        super.flush();
        record = this.toString();
        super.reset();

        if (record.length() == 0 || record.equals(lineSeparator)) {
          // avoid empty records
          return;
        }

        logger.logp(level, "", "", record);
      }
    }
  }
}
