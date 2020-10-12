#! /bin/bash
sed -i -e "s~RANGER_SERVICE_URL~${RANGER_SERVICE_URL}~g" /opt/ranger-atlas-plugin/install.properties
sed -i -e "s~ATLAS_REPOSITORY_NAME~${ATLAS_REPOSITORY_NAME}~g" /opt/ranger-atlas-plugin/install.properties
bash /opt/ranger-atlas-plugin/enable-atlas-plugin.sh
sleep 10