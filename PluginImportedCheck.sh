plugin_dir=$1
for dir in `ls "./apm-sniffer/$plugin_dir/"`; do
	for f in `find ./apm-sniffer/$plugin_dir/$dir -name *Instrumentation.java `; do
		NUM=`head -400 $f | grep import |grep -v net.bytebuddy. | grep -v org.apache.skywalking. |grep -v java.| wc -l`;
		if [ $NUM -gt 0 ] ; then
			echo "Plugin: $dir, never import any class unless JDK and ByteBuddy!";
			exit 1;
		fi
	done
done
