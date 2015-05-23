#!/usr/bin/env bash
BASEDIR=$(dirname $0)

rm -f $1
rm -f $1.temp
rm -f $1.temp.png

casperjs --ssl-protocol=any $BASEDIR/casper-test.js $1.temp

tidy $1.temp \
    |egrep '<li class=".* color.*">'\
    |sed 's/"/\\"/g' \
    |sed 's/<span>\(.*\)<\/span>/\1/' \
    |sed 's/<li class=\\"a color\\">\([^<]*\)<\/li>/{"date":"\1",/' \
    |sed 's/"date":"\(.*\)\/\(.*\)\/\(.*\)"/"date":"\3-\2-\1"/' \
    |sed 's/<li class=\\"b color\\">\([^<]*\)<\/li>/"place":"\1",/' \
    |sed 's/"place":"COMPRAS /"place":"/' \
    |sed 's/"place":"\(.*\)\*\(.*\)",/"place":"\1\2","status":"pending",/' \
    |sed 's/<li class=\\"c color\\">\([^<]*\)<\/li>/"reference":"\1",/' \
    |sed 's/<li class=\\"d color\\">\([^<]*\)<\/li>/"amount":"\1",/' \
    |sed 's/"amount":"\(.*\)\.\(.*\)"/"amount":"\1\2"/' \
    |sed 's/"amount":"\(.*\)$\(.*\)"/"amount":"\1\2"/' \
    |sed 's/<li class=\\"d color x\\">\([^<]*\)<\/li>/"payment":"card-\1",/' \
    |sed 's/<li class=\\"e color\\">\([^<]*\)<\/li>/"fee_number":"\1",/' \
    |sed 's/<li class=\\"f color\\">\([^<]*\)<\/li>/"fee_amount":"\1", "source":"falabella"},/' \
    |sed 's/"fee_amount":"\(.*\)\.\(.*\)"/"fee_amount":"\1\2"/' \
    |sed 's/"fee_amount":"\(.*\)$\(.*\)"/"fee_amount":"\1\2"/' \
    |sed 's/ "/"/g' \
    |sed '$s/,$/]/' \
    |sed '1s/{\(.*\)/[{\1/' \
    > $1