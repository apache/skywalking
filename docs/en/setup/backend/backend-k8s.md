# Deploy backend in kubernetes

Follow instructions in the [deploying SkyWalking backend to Kubernetes cluster](https://github.com/apache/skywalking-kubernetes#deploy-skywalking-backend-to-kubernetes-cluster)
 to deploy oap and ui to a kubernetes cluster.

#### Deploy SkyWalking with local files

##### **Package the corresponding version of the skywalking chart**

1.Configure the helm environment, [refer to Helm environment configuration](https://github.com/helm/helm). If you want to deploy the helm2 related chart, you can directly configure the relevant environment of helm2. The following example uses the Helm 3 environment.

2.Clone/download ZIP [**skywalking-kubernetes**](https://github.com/apache/skywalking-kubernetes) repo, the directory structure of the repo about chart is as follows

> helm-chart
>
> - helm2
>   - 6.0.0-GA
>   - 6.1.0
> - helm3
>   - 6.3.0
>   - 6.4.0

After the clone/download ZIP is completed, enter the specified directory and package the corresponding version of the chart.

```shell
cd skywalking-kubernetes/helm-chart/<helm-version>/<skywalking-version>
```

Note: helm-version is the corresponding helm version directory, skywalking-version is the corresponding skywalking version directory, and below is helm3 and skywalking 6.3.0.

```shell
cd skywalking-kubernetes/helm-chart/helm3/6.3.0
```

3.Since skywalking relies on elasticsearch as storage, execute the following command to update dependencies, which will be pulled from the official repo by default.

```shell
helm dep up skywalking
```

> Hang tight while we grab the latest from your chart repositories...
> ...Successfully got an update from the "stable" chart repository
> Update Complete. ⎈Happy Helming!⎈
> Saving 1 charts
> Downloading elasticsearch from repo https://kubernetes-charts.storage.googleapis.com/
> Deleting outdated charts

If the official repo does not exist, please add the official repository first.

```shell
helm repo add stable https://kubernetes-charts.storage.googleapis.com
```

> "stable" has been added to your repositories

4.Packing skywalking , execute the following command.

```shell
helm package skywalking/
```

> Successfully packaged chart and saved it to: C:\code\innerpeacez_github\skywalking-kubernetes\helm-chart\helm3\6.3.0\skywalking-0.1.0.tgz

After the package is completed, the .tgz file will be generated in the same directory of the current directory.

```
 ls
```

> skywalking/  skywalking-0.1.0.tgz

5.Deploy skywalking

```shell
helm install skywalking skywalking-0.1.0.tgz
```

> NAME: skywalking
> LAST DEPLOYED: 2019-10-08 14:58:56.390870576 +0800 CST m=+0.578158314
> NAMESPACE: default
> STATUS: deployed
>
> NOTES:
> Get the UI URL by running these commands:
>   export POD_NAME=$(kubectl get pods --namespace default -l "app=skywalking,release=skywalking,component=skywalking-ui" -o jsonpath="{.items[0].metadata.name}")
>   echo "Visit http://127.0.0.1:8080 to use your application"
>   kubectl port-forward $POD_NAME 8080:8080