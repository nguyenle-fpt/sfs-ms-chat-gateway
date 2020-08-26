#!/bin/sh

DEFAULT_BRANCH=master

CURRENT_BRANCH=$(echo $CODEBUILD_WEBHOOK_HEAD_REF | sed -e s/^refs\\/heads\\///)
if [ "$CURRENT_BRANCH" == "" ]; then
  CURRENT_BRANCH="$CODEBUILD_SOURCE_VERSION"
  if [ "$CURRENT_BRANCH" == "" ]; then
    CURRENT_BRANCH="$DEFAULT_BRANCH"
  fi
fi

echo "Current Branch is: $CURRENT_BRANCH"

# $1 = repo, $2 = branch
get_branch () {
  BRANCH_EXISTS=$(git ls-remote --heads $1 $2 | wc -l | xargs)
  if [ "$BRANCH_EXISTS" == "1" ]; then
    BRANCH_FOUND="$2"
  else
    BRANCH_FOUND="$DEFAULT_BRANCH"
  fi
  echo "$BRANCH_FOUND"
}

mkdir -p /tmp

PWD_DIR=$(pwd)

BRANCH=$(get_branch "https://github.com/SymphonyOSF/sfs-ms-chassis.git" "$CURRENT_BRANCH")
rm -Rf /tmp/sfs-ms-chassis
git clone -b $BRANCH https://github.com/SymphonyOSF/sfs-ms-chassis.git /tmp/sfs-ms-chassis
echo $BRANCH
cd /tmp/sfs-ms-chassis
./install-m2.sh -s "$PWD_DIR/settings.xml" -q

BRANCH=$(get_branch "https://github.com/SymphonyOSF/sfs-ms-admin.git" "$CURRENT_BRANCH")
rm -Rf /tmp/sfs-ms-admin
git clone -b $BRANCH https://github.com/SymphonyOSF/sfs-ms-admin.git /tmp/sfs-ms-admin
echo $BRANCH
cd /tmp/sfs-ms-admin
./install-m2.sh -s "$PWD_DIR/settings.xml" -q


cd $PWD_DIR
./install-m2.sh -s "$PWD_DIR/settings.xml" -q


BRANCH=$(get_branch https://github.com/SymphonyOSF/sfs-ms-whatsapp.git $CURRENT_BRANCH)
rm -Rf /tmp/sfs-ms-whatsapp
git clone -b $BRANCH https://github.com/SymphonyOSF/sfs-ms-whatsapp.git /tmp/sfs-ms-whatsapp
echo $BRANCH
cd /tmp/sfs-ms-whatsapp
./install-m2.sh -s "$PWD_DIR/settings.xml" -q

BRANCH=$(get_branch https://github.com/SymphonyOSF/sfs-ms-whatsapp-groups.git $CURRENT_BRANCH)
rm -Rf /tmp/sfs-ms-whatsapp-groups
git clone -b $BRANCH https://github.com/SymphonyOSF/sfs-ms-whatsapp-groups.git /tmp/sfs-ms-whatsapp-groups
echo $BRANCH
cd /tmp/sfs-ms-whatsapp-groups
./install-m2.sh -s "$PWD_DIR/settings.xml" -q
