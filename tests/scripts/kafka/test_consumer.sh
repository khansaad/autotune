#!/bin/bash
#
# Copyright (c) 2025 Red Hat, IBM Corporation and others.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Kafka Configuration
NAMESPACE="kafka"
BROKER="kruize-kafka-cluster-kafka-bootstrap.kafka:9092"
TOPICS=("recommendations-topic" "error-topic" "summary-topic")

# Consume messages from each topic
for TOPIC in "${TOPICS[@]}"; do
  echo "Consuming messages from topic: $TOPIC"
  oc exec -n $NAMESPACE kruize-kafka-cluster-kafka-0 -- bin/kafka-console-consumer.sh \
      --topic $TOPIC \
      --bootstrap-server kruize-kafka-cluster-kafka-bootstrap.kafka.svc.cluster.local:9092 \
      --from-beginning
done

echo "Test consumers are now running for all topics. Press Ctrl+C to exit."

# Wait for all background processes to complete
wait
