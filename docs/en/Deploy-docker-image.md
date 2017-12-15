- [Download source code](https://github.com/apache/incubator-skywalking/releases) and unzip source package. Execute following command under the unzipped directory.
 
```shell
> docker-compose pull
> docker-compose up
```
- The REST-service of collector listening on localhost:10800
- Open http://localhost:8080

Attention: The Docker Compose is only designed for you to run collector in your local machine. If you are running by using our provided docker compose, you can't access the ip:10800.

---

Test environment : docker 17.03.1-ce, docker compose 1.11.2
