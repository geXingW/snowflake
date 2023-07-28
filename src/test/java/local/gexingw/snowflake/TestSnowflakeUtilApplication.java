package local.gexingw.snowflake;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

/**
 * snowflake.
 *
 * @author GeXingW
 * @date 2023/7/28 11:15
 */
@SpringBootTest
public class TestSnowflakeUtilApplication {

    private final List<Long> ids = new ArrayList<>();

    @Test
    void testNextId() {
        ArrayList<Long> ids = new ArrayList<>(1000);

        for (int i = 0; i < 10000; i++) {
            Long id = SnowflakeUtil.getId();
            if (!ids.contains(id)) {
                ids.add(id);
                continue;
            }

            System.out.println("重复！");
        }
    }

    @Test
    void testConcurrentNextId() {
        System.out.println(System.currentTimeMillis());

        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                for (int j = 0; j < 100000; j++) {
                    Long id = SnowflakeUtil.getId();
                    if (!ids.contains(id)) {
                        ids.add(id);
                        continue;
                    }

                    System.out.println("重复！");
                }
            }).start();
        }

        System.out.println(System.currentTimeMillis());
    }

}
