package local.gexingw.snowflake;

/**
 * @author GeXingW
 */
public class SnowflakeUtil {

    private static final Snowflake SNOWFLAKE_INSTANCE;

    static {
        SNOWFLAKE_INSTANCE = new Snowflake();
    }

    public static long nextId() {
        return SNOWFLAKE_INSTANCE.nextId();
    }

}
