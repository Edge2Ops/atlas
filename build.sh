#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


echo "printing environment variables..."
printenv

echo "Configuring aws default profile..."

aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID --profile awsdeploy
aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY --profile awsdeploy
aws configure set region ap-south-1 --profile awsdeploy


mkdir ~/.m2

wget https://atlan-build-artifacts.s3-ap-south-1.amazonaws.com/artifact/maven_local_repository.zip
unzip maven_local_repository.zip -d ~/.m2


echo "printing aws profiles..."
echo "$(<~/.aws/credentials )"

echo "Maven Building"
mvn clean -DskipTests package -Pdist

echo "Sending build to s3"
aws s3 cp distro/target/apache-atlas-3.0.0-SNAPSHOT-server.tar.gz s3://atlan-build-artifacts/atlas/
