# easy-http
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/vip.justlive/easy-http/badge.svg)](https://maven-badges.herokuapp.com/maven-central/vip.justlive/easy-http/)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

easy to contact http rest api


## 介绍

- 基于Spring mvc`@RequestMapping`、`@GetMapping`、`@PostMapping`、`@RequestParam`、`RequestHeader`、`PathVariable`等注解扩展
- 自动扫描接口实例化并托管至Spring，参考自Mybatis自动扫描Mapper
- 需要声明的接口和Server端保持一致即可完美对接

## 快速开始

### 引入

创建`Maven`项目

```xml
<dependency>
    <groupId>vip.justlive</groupId>
    <artifactId>easy-http</artifactId>
    <version>${lastVersion}</version>
</dependency>
```

或`Gradle`

```
compile 'vip.justlive:easy-http:$lastVersion'
```

### 使用方式

```java

// 创建接口

@HttpClient
@RequestMapping("https://api.github.com")
public interface GithubApi {

  @RequestMapping(method = RequestMethod.GET)
  String root();


  @GetMapping("/repos/{owner}/{repo}")
  Repository repos(@PathVariable String owner, @PathVariable String repo);

  @Data
  class Repository {

    private Long id;
    private String name;
    private int forks;
    private int watchers;
  }
}

// 使用${xx}获取配置
@HttpClient
@RequestMapping("${thirdpart.api.url}")
public interface ThirdpartApi {

  @PostMapping("/api/token")
  RespVo<AccessToken> token(@RequestParam String appKey, @RequestParam String appSecret);

  @PostMapping("/api/account")
  RespVo<Account> account(@RequestHeader String accessToken, @RequestBody Account account);

  @Data
  @Accessors(chain = true)
  class AccessToken {

    private String accessToken;
    private Long expiresIn;
  }

  @Data
  @Accessors(chain = true)
  class Account {

    private String name;
    private BigDecimal balance;
  }
}

// 增加扫描（默认为当前类所处包）

@HttpClientScan("com.xxx")
@Configuration
public class HttpClientAutoConfiguration {

}

// 使用

@Slf4j
@Component
public class Demo {

  @Autowired
  private GithubApi githubApi;

  @Autowired
  private ThirdpartApi thirdpartApi;

  @PostConstruct
  private void init() {
   
    String result = githubApi.root();
    log.info("githubApi root {}", result);

    Repository repository = githubApi.repos("justlive1", "easy-http");
    log.info("githubApi repos {}", repository);
    
    AccessToken token = thirdpartApi.token("key", "secret").getData();
    log.info("thirdpartApi token {}", token);

    Account account = new Account().setName("aa").setBalance(new BigDecimal("12.3"));
    account = thirdpartApi.account(token.getAccessToken(), account).getData();
    log.info("thirdpartApi account {}", account);
  }
}
```

