#!/bin/bash

[ -z "$DRY_RUN" ] && DRY_RUN="false"
# path of file to parse for intended configuration
[ -z "$CONFIG_PATH" ] && CONFIG_PATH="/etc/jitsi/jigasi/sip-communicator.properties"

CC_MUC_BASE_URL="http://localhost:8788/configure/call-control-muc"
CC_MUC_LIST_URL="$CC_MUC_BASE_URL/list"
CC_MUC_ADD_URL="$CC_MUC_BASE_URL/add"
CC_MUC_REMOVE_URL="$CC_MUC_BASE_URL/remove"

CC_MUC_PREFIX="net.java.sip.communicator.impl.protocol.jabber"
ESCAPE_PREFIX="${CC_MUC_PREFIX//\./\\.}"

# get all server ids from the configuration files
CONFIG_SERVER_IDS="$(grep "$CC_MUC_PREFIX.acc" $CONFIG_PATH \
    | cut -d '.' -f 8 \
    | grep '=' \
    | cut -d '=' -f2)"

[[ "$DRY_RUN" == "true" ]] && echo "Found Server IDs: $CONFIG_SERVER_IDS"

# init processed server ids list
SERVER_IDS="[]"
for SERVER_ID in $CONFIG_SERVER_IDS; do
    SERVER_IDS="$(echo "[\"$SERVER_ID\"]" $SERVER_IDS | jq -s add)"
    # munge the properties file for this server into json
    CONFIG_JSON="$(grep "$CC_MUC_PREFIX.$SERVER_ID\." $CONFIG_PATH \
            | grep -v '^#' \
            | sed "s/$ESCAPE_PREFIX\.$SERVER_ID\./\"/g" \
            | sed 's/=/":"/' \
            | sed 's/$/",/')"
    # build the json for the configuration call
    echo "{$CONFIG_JSON\"id\":\"$SERVER_ID\"}" | jq '. + {"PASSWORD": .PASSWORD|@base64d}' > /tmp/cc-muc.json
    if [ "$DRY_RUN" == "true" ]; then
        echo "Would Add MUC: $SERVER_ID"
        cat /tmp/cc-muc.json | grep -v 'PASSWORD' | jq
    else
        echo "Adding MUC: $SERVER_ID"
        curl -s -H"Content-Type: application/json" -X POST -d @/tmp/cc-muc.json $CC_MUC_ADD_URL
    fi
    rm /tmp/cc-muc.json
    i=$((i+1))
done

MUC_LIST=$(curl -s $CC_MUC_LIST_URL)
# set empty list if no MUCs are found
if [[ $? -gt 0 ]]; then
    echo "Failed to get muc list, setting to empty list"
    MUC_LIST="[]"
fi

CC_MUC_REMOVE_LIST=$(echo "$MUC_LIST" | jq -r ". - $SERVER_IDS | .[]")

for MUC in $CC_MUC_REMOVE_LIST; do
    if [ "$DRY_RUN" == "true" ]; then
        echo "Would Remove MUC: $MUC"
    else
        echo "Removing MUC: $MUC"
        curl -s -H"Content-Type: application/json" -X POST -d "{\"id\":\"$MUC\"}" $CC_MUC_REMOVE_URL
    fi
done
