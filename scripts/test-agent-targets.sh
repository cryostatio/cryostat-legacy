
parent_dir="$(dirname "$(dirname "$(readlink -fm "$0")")")"
"$parent_dir/smoketest.sh"
