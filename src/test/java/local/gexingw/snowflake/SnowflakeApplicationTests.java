package local.gexingw.snowflake;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SnowflakeApplicationTests {

    @Autowired
    Snowflake snowflake;

    private final int testCount = 10;

    @Test
    void contextLoads() {
    }

    @Test
    void testSnowflakeAutowired(){
        System.out.println("自动注入生成：");
        for (int i = 0; i < testCount; i++) {
            System.out.println(snowflake.nextId());
        }
    }

    @Test
    void testSnowflakeWithOutParams() {
        Snowflake snowflake = new Snowflake();
        System.out.println("不指定参数生成：");
        for (int i = 0; i < testCount; i++) {
            System.out.println(snowflake.nextId());
        }
    }

    @Test
    void testSnowflakeWithDataCenterAndMachine() {
        System.out.println("指定数据中心和机器码：");
        Snowflake snowflake = new Snowflake(1L, 2L);
        for (int i = 0; i < testCount; i++) {
            System.out.println(snowflake.nextId());
        }
    }

    @Test
    void testSnowflakeWithCustomBits() {
        System.out.println("自定义字符长度：");
        Snowflake snowflake = new Snowflake(1412092800000L, 1L, 2L, 5L,1L, 3L, 4L);
        for (int i = 0; i < testCount; i++) {
            System.out.println(snowflake.nextId());
        }
    }
}
