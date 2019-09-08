MYSQL_ROOT="root"  # mysql root uzivatel
MYSQL_ROOTPW="" # heslo roota do mysql databaze
MYSQL_USER="travis"  # jmeno uzivatele ktery pristuje do databaze
MYSQL_HOST="localhost" # jmeno stroje, na kterem bezi databaze
MYSQL_DB="devel"   # jmeno databaze
MYSQL_PASSWORD="" # pristupove heslo
MYSQL_SCHEME="bin/sql/create_mysql_scheme.sql"
MYSQL_DATA="misc/databaze.sql.bz2"

echo "Creating SQL database"
mysql -u $MYSQL_ROOT --password=$MYSQL_ROOTPW --default-character-set=utf8 <<EOF 
DROP DATABASE IF EXISTS $MYSQL_DB;
CREATE DATABASE $MYSQL_DB default character set utf8 collate utf8_czech_ci;
USE $MYSQL_DB;
GRANT ALL ON *.* TO $MYSQL_USER@$MYSQL_HOST IDENTIFIED BY '$MYSQL_PASSWORD' WITH GRANT OPTION;
FLUSH PRIVILEGES;
EOF

echo "Feeding database witch scheme and example data"
bzcat $MYSQL_DATA | mysql -u $MYSQL_USER --password=$MYSQL_PASSWORD --default-character-set=utf8 $MYSQL_DB