package local.gexingw.snowflake;

import java.util.concurrent.ThreadLocalRandom;

/**
 * snowflake.
 *
 * @author GeXingW
 * @date 2023/7/27 13:30
 */
public class SnowflakeUtil {

    /**
     * 起始时间
     * 1412092800000 按 yyyy-MM-DD HH:mm:ss SSS 格式转换为 2014-10-01 00:00:00
     * 该时间设定值个人觉得应当分两种情况：
     * 1、只需要生成唯一递增ID，生成解决的位数，不影响系统正常使用
     * 2、需要生成定长(18或19位)唯一递增ID，定长19位需要将起始时间设为当前时间的8(7.5 ≈ 8)年前
     */
    private final long startTimeMills = 1420041600000L;

    /**
     * 记录上一次生成雪花算法Id时的序列号
     * <p>
     * 记录上一次的生成序列号是非常有必要的，雪花算法能够保持持续递增，取决于三个组成部分：
     * 1、保持持续增长的时间戳部分
     * 2、保持不变的机器标识码部分
     * 3、保持统一毫秒内持续增长的序列号部分
     */
    private long lastSequence = 0;

    /**
     * 同一毫秒内序列号所占位数
     * 2^12 - 1 = 4095
     * <p>
     * 也就是说每毫秒内可生成 4095个序列号
     * <p>
     */
    private final long sequenceBits = 12L;

    /*
     * 每毫秒内可共生成的最大序列号
     * 序列号可表示范围为 0 ~ 4095，大于4095程序跳到下一毫秒进行生成
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final long maxSequence = ~(-1L << sequenceBits);

    /**
     * 运行改程序的机器在该数据中心的唯一Id
     */
    @SuppressWarnings("UnusedAssignment")
    private long machineId = 1L;

    /**
     * 机器Id标识码
     * 5位 可表示范围为：2^5 - 1 = 31
     * <p>
     * 配合数据中心使用，可表示范围为：2^10 - 1 = 1023
     * <p>
     */
    private final long machineIdBits = 5L;

    /**
     * 机器Id标识码
     * 5位 可表示范围为：2^5 - 1 = 31
     * <p>
     * 配合数据中心使用，可表示范围为：2^10 - 1 = 1023
     * <p>
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final long machineIdOffset = sequenceBits;


    /**
     * 运行该程序的机器所处数据中心Id
     * <p>
     */
    @SuppressWarnings("UnusedAssignment")
    private long dataCenterId = 1L;

    /**
     * 数据中心ID标识码
     * 5位 可表示范围为：2^5 - 1 = 31
     */
    private final long dataCenterIdBits = 5L;

    /**
     * 数据中心Id在64为结果中从右到左所占位数
     * 结果为：机器标识码Id所占位数 + 序列号所占位数
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final long dataCenterIdOffset = machineIdBits + sequenceBits;

    /**
     * 时间戳在64位结果中从右到左的起始位置
     * 结果为: 数据中心所展位数 + 机器表示码Id所占位数 + 序列号所占位数
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final long timestampOffset = dataCenterIdBits + machineIdBits + sequenceBits;


    /**
     * 上次Id生成的毫秒数
     */
    private long lastTimeMills = startTimeMills;

    /**
     * 允许始终回拨的最大毫秒数
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final long allowTimeBackwardMS = 5L;

    /**
     * 生成的静态实例
     */
    private static final SnowflakeUtil SNOWFLAKE_UTIL;

    static {
        SNOWFLAKE_UTIL = new SnowflakeUtil(1, 2);
    }

    public SnowflakeUtil(long dataCenterId, long machineId) {
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
    }

    /**
     * 根据时间戳、机器标识码和序列号生成持续递增的 针对该机器标识码唯一 的雪花算法Id
     *
     * @return snowflakeId
     */
    public static Long getId() {
        return SNOWFLAKE_UTIL.nextId();
    }

    /**
     * 根据时间戳、机器标识码和序列号生成持续递增的 针对该机器标识码唯一 的雪花算法Id
     *
     * @return snowflakeId
     */
    public synchronized Long nextId() {
        // 当前时间毫秒数
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis < lastTimeMills) {
            // 回拨时间
            long timestampOffset = lastTimeMills - currentTimeMillis;
            if (timestampOffset > this.allowTimeBackwardMS) {   // 如果时钟回拨范围大于允许的最大回拨范围，抛出异常
                throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", timestampOffset));
            }

            try {
                wait(timestampOffset);  // 如果回拨时间范围 小于 允许的最大回拨范围，程序等待
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            currentTimeMillis = getCurrentTimestamp();
        }

        // 记录本次生成的序列号
        this.lastSequence = (this.lastSequence + 1) & maxSequence;

        /*
         * this.lastSequence 为 0，代表当前时间戳已经达到 序列号最大值(maxSequence)，分两种情况处理：
         *  1、如果 当前时间戳 与 所记录的上次生成时间戳 相同，则当前时间戳跳到下一毫秒，并将序列号重新置为起始状态
         *  2、如果 当前时间戳 大于 所记录的上次生成时间戳，则直接将当前时间戳跳到下一毫秒，并将序列号重新置为起始状态
         *  3、当前时间戳 小于 所记录的上次生成时间戳 的情况即为 时钟回拨的情况，按时钟回拨已进行处理，不在考虑
         */
        if (this.lastSequence == 0L) {
            if (currentTimeMillis == this.lastTimeMills) { // 相同时间戳改为下一个时间戳
                currentTimeMillis = this.getNextTimestamp(currentTimeMillis);
            }

            this.lastSequence = ThreadLocalRandom.current().nextLong(0, 3);
        }

        // 记录本次生成序列号的毫秒时间戳
        this.lastTimeMills = currentTimeMillis;

        return ((currentTimeMillis - startTimeMills) << timestampOffset)
                | (dataCenterId << dataCenterIdOffset)
                | (machineId << machineIdOffset)
                | this.lastSequence;
    }

    /**
     * 获取比当前毫秒时间戳大的下一个毫秒时间戳
     *
     * @param currentTimestamp 上前的时间戳
     * @return 下一个毫秒时间戳
     */
    private long getNextTimestamp(long currentTimestamp) {
        long nextTimestamp = getCurrentTimestamp();

        while (nextTimestamp <= currentTimestamp) {
            nextTimestamp = getCurrentTimestamp();
        }

        return nextTimestamp;
    }

    /**
     * 获取当前毫秒级时间戳
     *
     * @return 当前时间的毫秒时间戳
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

}
