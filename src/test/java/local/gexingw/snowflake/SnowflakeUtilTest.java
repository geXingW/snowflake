package local.gexingw.snowflake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class SnowflakeUtilTest {

    @Test
    public void testNextId() {
        for (int i = 0; i < 50; i++) {
            System.out.println(SnowflakeUtil.nextId());
        }
    }
}
