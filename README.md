## Snowflake(Java)

基于雪花算法实现的 **持续递增的唯一ID** 生成方式。

### 特色：

1. 针对始终回拨有一定的容许范围
2. 解决跨毫秒起始值每次为0开始的情况
3. 支持SpringBoot的配置文件配置
4. 支持自定义数据中心ID、机器标识码和序列号的所占位数，可实现自定义结果长度
5. 优化一点生成逻辑

### 用法：
1. 常规用法：
```yaml
# application.yml        
snowflake:
    data-center-id-bits: 5
    machine-id-bits: 4
    sequence-bits: 12
    data-center-id: 1
    machine-id: 10
    allow-time-backward-m-s: 6
```
```java
@SpringBootTest
class SnowflakeTests {

    @Autowired
    Snowflake snowflake;

    private final int testCount = 10;

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
```

2.自定义生成长度：
 1. 因为时间戳部分采用的是时间戳之差，建议起始时间设为8年前，在保证时间戳位数不变的情况下，你仍可以使用 `69 - 8 = 61`年
 2. 为了使结果长度更短，可以缩短 `数据中心`、`机器码`或`序列号`位数，如下方式可生成14个字符的唯一递增ID：
```yml
# application.yml 
snowflake:
    data-center-id-bits: 1
    machine-id-bits: 3
    sequence-bits: 4
    data-center-id: 1
    machine-id: 2
    allow-time-backward-m-s: 6
```
```text
56680906841761
56680906841762
56680906841763
56680906841764
56680906841765
56680906841766
56680906841767
56680906842024
56680906842025
56680906842026
```

## 鸣谢
许多地方是参考两位大佬的代码：

1. [分布式高效ID生产黑科技](https://gitee.com/yu120/sequence)
2. [mybatis-plus](https://github.com/baomidou/mybatis-plus)

大佬辛苦了！
