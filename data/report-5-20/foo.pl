#!/bin/perl
open INPUT, $ARGV[0] or die "Can't open $ARGV[0]\n";
$mbs = 0;
$lat = 0;
$cmp = 0;
$cnt = 0;
while (<INPUT>) {
	if (/([0-9]+\.[0-9]+)[^(0-9)]*([0-9]+\.[0-9]+)[^(0-9)]*([0-9]+\.[0-9]+).*/) {
	if ($cnt>1) {
	$mbs += $1;
	$lat += $2;
	$cmp += $3;
        }
	$cnt++;
#	print "$1 $2 $3\n";
}
}
$cnt = $cnt-2;

$mbs = $mbs/$cnt;
$lat = $lat/$cnt;
$cmp = $cmp/$cnt;

print "$mbs\t$lat\t$cmp\n";
