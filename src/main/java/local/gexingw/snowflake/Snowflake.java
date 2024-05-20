package local.gexingw.snowflake;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConfigurationProperties(prefix = "snowflake")
public class Snowflake {

    /**
     * 起始时间
     * 1412092800000 按 yyyy-MM-DD HH:mm:ss SSS 格式转换为 2014-10-01 00:00:00
     * 该时间设定值个人觉得应当分两种情况：
     * 1、只需要生成唯一递增ID，生成解决的位数，不影响系统正常使用
     * 2、需要生成定长(18或19位)唯一递增ID，定长19位需要将起始时间设为当前时间的8(7.5 ≈ 8)年前
     * <p>
     * 配置文件配置：snowflake.start-timestamp
     */
    private long startTimestamp = 1380556800000L;

    /**
     * 总得字符长度
     */
    private final static long TOTAL_BITS = 63L;

    /**
     * 标准的雪花算法各部分所占位数
     */
    private final static long STANDARD_TIMESTAMP_BITS = 41L;
    private final static long STANDARD_DATA_CENTER_ID_BITS = 5L;
    private final static long STANDARD_MACHINE_ID_BITS = 5L;
    private final static long STANDARD_SEQUENCE_BITS = 12L;

    /**
     * 时间戳所占的二进制位数
     * 41位 可表示范围为: 2^41 - 1 ≈ 69.73(年) * 365(天) * 24(时) * 3600 (秒)
     * <p>
     * 也就是大家所说的可用 69 年的出处
     * <p>
     * 自定义配置文件配置：snowflake.timestamp-bits
     */
    private final long timestampBits = 41L;

    /**
     * 数据中心ID标识码
     * 5位 可表示范围为：2^5 - 1 = 31
     * <p>
     * 自定义配置文件配置：snowflake.data-center-id-bits
     */
    private long dataCenterIdBits = 5L;

    /**
     * 机器Id标识码
     * 5位 可表示范围为：2^5 - 1 = 31
     * <p>
     * 配合数据中心使用，可表示范围为：2^10 - 1 = 1023
     * <p>
     * 自定义配置文件配置：snowflake.machine-id-bits
     */
    private long machineIdBits = 5L;

    /**
     * 同一毫秒内序列号所占位数
     * 2^12 - 1 = 4095
     * <p>
     * 也就是说每毫秒内可生成 4095个序列号
     * <p>
     * 自定义配置文件配置：snowflake.sequence-bits
     */
    private long sequenceBits = 12L;


    /**
     * 可使用的最大数据中心ID
     * 设置该值是为了避免传入数据中心ID过大，超出可用位数限制
     * 数据中心Id可表示范围为 0 ~ 31，大于31就是超出了，程序进行取模处理
     */
    private long maxDataCenterId = this.calcMaxValueByBits(this.dataCenterIdBits);

    /**
     * 可使用的最大机器码ID
     * 设置该值是为了避免传入机器码过大，超出可用位数限制
     * 机器码Id可表示范围为 0 ~ 31，大于31就是超出了，程序进行取模处理
     */
    private long maxMachineId = this.calcMaxValueByBits(this.machineIdBits);

    /**
     * 每毫秒内可共生成的最大序列号
     * 序列号可表示范围为 0 ~ 4095，大于4095程序跳到下一毫秒进行生成
     */
    private long maxSequence = this.calcMaxValueByBits(this.sequenceBits);

    /**
     * 时间戳在64位结果中从右到左的起始位置
     * 结果为: 数据中心所展位数 + 机器表示码Id所占位数 + 序列号所占位数
     */
    private long timestampOffset = this.dataCenterIdBits + this.machineIdBits + this.sequenceBits;

    /**
     * 数据中心Id在64为结果中从右到左所占位数
     * 结果为：机器标识码Id所占位数 + 序列号所占位数
     */
    private long dataCenterIdOffset = this.machineIdBits + this.sequenceBits;

    /**
     * 机器标识码Id在64为结果中从右到左所占位数
     * 结果为：序列号所占位数
     */
    private long machineIdOffset = this.sequenceBits;

    /**
     * 记录上一次生成雪花算法Id时的时间戳，记录该值主要用于检测时钟回拨：
     * <p>
     * 如果 已记录上一次生成的时间戳 > 当前时间戳 ，即发生了时钟回拨情况
     */
    private long lastTimestamp = 0L;

    /**
     * 记录上一次生成雪花算法Id时的序列号
     * <p>
     * 记录上一次的生成序列号是非常有必要的，雪花算法能够保持持续递增，取决于三个组成部分：
     * 1、保持持续增长的时间戳部分
     * 2、保持不变的机器标识码部分
     * 3、保持统一毫秒内持续增长的序列号部分
     */
    private long lastSequence = 0L;

    /**
     * 运行该程序的机器所处数据中心Id
     * <p>
     * 自定义配置文件配置：snowflake.data-center-id
     */
    private long dataCenterId = 1L;

    /**
     * 运行改程序的机器在该数据中心的唯一Id
     * <p>
     * 自定义配置文件配置：snowflake.machine-id
     */
    private long machineId = 1L;

    /**
     * 允许始终回拨的最大毫秒数
     * <p>
     * 自定义配置文件配置：snowflake.allow-time-backward-m-s
     */
    private long allowTimeBackwardMs = 5L;

    private static Snowflake snowflake = new Snowflake();

    static {
        snowflake = new Snowflake();
    }

    public Snowflake() {
    }

    public Snowflake(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public Snowflake(long dataCenterId, long machineId) {
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
    }

    public Snowflake(long startTimestamp, long dataCenterId, long machineId, long allowTimeBackwardMs) {
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
        this.startTimestamp = startTimestamp;
        this.allowTimeBackwardMs = allowTimeBackwardMs;
    }

    public Snowflake(long startTimestamp, long dataCenterId, long machineId, long allowTimeBackwardMs, long dataCenterIdBits, long machineIdBits, long sequenceBits) {
        this.validBitsSum(dataCenterIdBits, machineIdBits, sequenceBits);

        this.startTimestamp = startTimestamp;
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
        this.allowTimeBackwardMs = allowTimeBackwardMs;
        this.dataCenterIdBits = dataCenterIdBits;
        this.machineIdBits = machineIdBits;
        this.sequenceBits = sequenceBits;
    }

    public static Long getId() {
        return snowflake.nextId();
    }

    /**
     * 根据时间戳、机器标识码和序列号生成持续递增的 针对该机器标识码唯一 的雪花算法Id
     *
     * @return snowflakeId
     */
    public synchronized long nextId() {
        /**
         * 如果各部分所占位数进行了自定义：
         *     1、重新计算各部分从左到右起始位数
         *     2、重新计算各部分允许的最大值
         */
        if (this.hasCustomBits()) {
            // 重新计算各部分从左到右起始位数
            this.reCalcBitsMax();
            // 重新计算各部分允许的最大值
            this.reCalcBitsOffset();
        }

        long currentTimestamp = this.getCurrentTimestamp();

        /**
         * 如果当前时间小于上次生成的时间，可能发生的时钟回拨情况
         *
         * 1、如果回拨时间范围 小于 允许的最大回拨范围，程序等待
         * 2、如果回拨时间范围 大于 允许的最大回拨范围，抛出异常
         */
        if (currentTimestamp < this.lastTimestamp) {
            long timestampOffset = this.lastTimestamp - currentTimestamp;
            // 如果时钟回拨范围大于允许的最大回拨范围，抛出异常
            if (timestampOffset > this.allowTimeBackwardMs) {
                throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", timestampOffset));
            }

            try {
                // 如果回拨时间范围 小于 允许的最大回拨范围，程序等待
                wait(timestampOffset);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            /**
             * 等待结束，即认定为可以继续生成Id
             * 重新获取用于生成最终结果的 时间戳
             */
            currentTimestamp = this.getCurrentTimestamp();
        }

        this.lastSequence = (this.lastSequence + 1) & maxSequence;

        /**
         * this.lastSequence 为 0，代表当前时间戳已经达到 序列号最大值(maxSequence)，分两种情况处理：
         *  1、如果 当前时间戳 与 所记录的上次生成时间戳 相同，则当前时间戳跳到下一毫秒，并将序列号重新置为起始状态
         *  2、如果 当前时间戳 大于 所记录的上次生成时间戳，则直接将当前时间戳跳到下一毫秒，并将序列号重新置为起始状态
         *  3、当前时间戳 小于 所记录的上次生成时间戳 的情况即为 时钟回拨的情况，按时钟回拨已进行处理，不在考虑
         */
        if (this.lastSequence == 0L) {
            if (currentTimestamp == this.lastTimestamp) { // 相同时间戳改为下一个时间戳
                currentTimestamp = this.getNextTimestamp(this.lastTimestamp);
            }

            this.lastSequence = ThreadLocalRandom.current().nextLong(0, 3);
        }

        // 记录当前所生成的时间戳，用于下一次生成进行比对
        this.lastTimestamp = currentTimestamp;

        return ((currentTimestamp - this.startTimestamp) << this.timestampOffset)
                | (this.dataCenterId << this.dataCenterIdOffset)
                | (this.machineId << this.machineIdOffset)
                | this.lastSequence;
    }

    private boolean hasCustomBits() {
        if (this.dataCenterIdOffset != STANDARD_DATA_CENTER_ID_BITS) {
            return true;
        }

        if (this.machineIdOffset != STANDARD_MACHINE_ID_BITS) {
            return true;
        }

        //noinspection RedundantIfStatement
        if (this.sequenceBits != STANDARD_SEQUENCE_BITS) {
            return true;
        }

        return false;
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public void setDataCenterIdBits(long dataCenterIdBits) {
        this.dataCenterIdBits = dataCenterIdBits;
    }

    public void setMachineIdBits(long machineIdBits) {
        this.machineIdBits = machineIdBits;
    }

    public void setSequenceBits(long sequenceBits) {
        this.sequenceBits = sequenceBits;
    }

    /**
     * 抄自：
     * 项目：mybatis-plus
     * 源代码包名：om.baomidou.mybatisplus.core.toolkit.Sequence;
     * 地址：https://github.com/baomidou/mybatis-plus
     * <p>
     * 如果没有手动设定 数据中心Id，程序为保证正常运行，自动计算一个数据中心Id，
     * 计算出的结果是根据最大数据中心Id取模后的，不会超出数据中心Id的标识范围
     *
     * @param maxDataCenterId
     * @return
     */
    protected long getDataCenterId(long maxDataCenterId) {
        long id = 0L;
        try {
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);

            if (network == null) {
                id = 1L;
            } else {
                byte[] mac = network.getHardwareAddress();
                if (null != mac) {
                    id = ((0x000000FF & (long) mac[mac.length - 2]) | (0x0000FF00 & (((long) mac[mac.length - 1]) << 8))) >> 6;
                    id = id % (maxDataCenterId + 1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return id;
    }

    /**
     * 抄自：
     * 项目：mybatis-plus
     * 源代码包名：com.baomidou.mybatisplus.core.toolkit.Sequence;
     * 地址：https://github.com/baomidou/mybatis-plus
     */
    protected long getMachineId(long maxMachineId) {
        StringBuilder mPid = new StringBuilder();
        mPid.append(dataCenterId);
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (this.isBlank(name)) {
            /*
             * GET jvmPid
             */
            mPid.append(name.split("@")[0]);
        }
        /*
         * MAC + PID 的 hashcode 获取16个低位
         */
        return (mPid.toString().hashCode() & 0xffff) % (maxMachineId + 1);
    }

    /**
     * 抄自：
     * 项目：mybatis-plus
     * 源代码包名：com.baomidou.mybatisplus.core.toolkit.StringUtils;
     * 地址：https://github.com/baomidou/mybatis-plus
     */
    private boolean isBlank(CharSequence cs) {
        if (cs != null) {
            int length = cs.length();
            for (int i = 0; i < length; i++) {
                if (!Character.isWhitespace(cs.charAt(i))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 获取比当前毫秒时间戳大的下一个毫秒时间戳
     *
     * @param currentTimestamp
     * @return
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
     * @return
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 校验自定义的各项字符位数，是否符合雪花算法组成要求：
     * 总数之和可以小于或等于 63位，但不能大于63位
     *
     * @param dataCenterIdBits
     * @param machineIdBits
     * @param sequenceBits
     */
    private void validBitsSum(long dataCenterIdBits, long machineIdBits, long sequenceBits) {
        if (this.TOTAL_BITS < this.timestampBits + dataCenterIdBits + machineIdBits + sequenceBits) {
            throw new RuntimeException(String.format("The sum of bits should not over %d", this.TOTAL_BITS));
        }
    }

    /**
     * 验证当前设定的 数据中心Id 和 机器标识Id 是否超过各自最大值
     *
     * @param dataCenterId
     * @param machineId
     */
    private void validDataCenterIdAndMachineId(long dataCenterId, long machineId) {
        if (dataCenterId > this.maxDataCenterId) {
            throw new RuntimeException(String.format("Data center Id can't be greater than %d or less than 0!", this.maxDataCenterId));
        }

        if (machineId > this.maxMachineId) {
            throw new RuntimeException(String.format("Machine Id can't be greater than %d or less than 0!", this.maxMachineId));
        }
    }

    /**
     * 重新各部分从左到右的起始位数
     */
    private void reCalcBitsOffset() {
        this.timestampOffset = this.dataCenterIdBits + this.machineIdBits + this.sequenceBits;
        this.dataCenterIdOffset = this.machineIdBits + this.sequenceBits;
        this.machineIdOffset = this.sequenceBits;
    }

    /**
     * 重新计算各部分的最大值
     */
    private void reCalcBitsMax() {
        this.maxDataCenterId = this.calcMaxValueByBits(this.dataCenterIdBits);
        this.maxMachineId = this.calcMaxValueByBits(this.machineIdBits);
        this.maxSequence = this.calcMaxValueByBits(this.sequenceBits);

        // 验证当前设定的 数据中心Id 和 机器标识Id 是否超过各自最大值
        this.validDataCenterIdAndMachineId(this.dataCenterId, this.machineId);
    }

    private long calcMaxValueByBits(long bits) {
        return ~(-1L << bits);
    }

}
