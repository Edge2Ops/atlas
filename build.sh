echo "printing environment variables..."
printenv

echo "Configuring aws default profile..."

aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID --profile awsdeploy
aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY --profile awsdeploy
aws configure set region eu-west-1 --profile awsdeploy

echo "printing aws profiles..."
echo "$(<~/.aws/credentials )"

echo "Maven Building"
mvn clean -DskipTests package -Pdist

echo "Sending build to s3"
aws s3 cp distro/target/apache-atlas-3.0.0-SNAPSHOT-server.tar.gz s3://atlan-public/atlas/ --acl public-read