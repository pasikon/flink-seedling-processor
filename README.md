A Flink application project using Scala and SBT.

To run and test your application use SBT invoke: 'sbt run'

In order to run your application from within IntelliJ, you have to select the classpath of the 'mainRunner' module in the run/debug configurations.
Simply open 'Run -> Edit configurations...' and then select 'mainRunner' from the "Use classpath of module" dropbox.


helm repo add incubator http://storage.googleapis.com/kubernetes-charts-incubator
helm install --name kafka011 incubator/kafka --set imageTag=3.3.1

```
NAME:   kafka011
LAST DEPLOYED: Thu Oct  4 18:20:55 2018
NAMESPACE: default
STATUS: DEPLOYED

RESOURCES:
==> v1/Service
NAME                         AGE
kafka011-zookeeper-headless  0s
kafka011-zookeeper           0s
kafka011                     0s
kafka011-headless            0s

==> v1beta1/StatefulSet
kafka011-zookeeper  0s
kafka011            0s

==> v1beta1/PodDisruptionBudget
kafka011-zookeeper  0s

==> v1/Pod(related)

NAME                  READY  STATUS             RESTARTS  AGE
kafka011-zookeeper-0  0/1    ContainerCreating  0         0s
kafka011-0            0/1    Pending            0         0s


NOTES:
### Connecting to Kafka from inside Kubernetes

You can connect to Kafka by running a simple pod in the K8s cluster like this with a configuration like this:

  apiVersion: v1
  kind: Pod
  metadata:
    name: testclient
    namespace: default
  spec:
    containers:
    - name: kafka
      image: confluentinc/cp-kafka:3.3.1
      command:
        - sh
        - -c
        - "exec tail -f /dev/null"

Once you have the testclient pod above running, you can list all kafka
topics with:

  kubectl -n default exec testclient -- /usr/bin/kafka-topics --zookeeper kafka011-zookeeper:2181 --list

To create a new topic:

  kubectl -n default exec testclient -- /usr/bin/kafka-topics --zookeeper kafka011-zookeeper:2181 --topic test1 --create --partitions 1 --replication-factor 1

To listen for messages on a topic:

  kubectl -n default exec -ti testclient -- /usr/bin/kafka-console-consumer --bootstrap-server kafka011-kafka:9092 --topic test1 --from-beginning

To stop the listener session above press: Ctrl+C

To start an interactive message producer session:
  kubectl -n default exec -ti testclient -- /usr/bin/kafka-console-producer --broker-list kafka011-kafka-headless:9092 --topic test1

To create a message in the above session, simply type the message and press "enter"
To end the producer session try: Ctrl+C

```
