# Точку входа LSPosed/Vector называет assets/xposed_init — R8 об этом не знает
-keep class com.github.vorobeyyyyyy.geelynavbar.hook.Entry { *; }
# Имена в стеках ошибок хука (они попадают в лог Vector)
-keepnames class com.github.vorobeyyyyyy.geelynavbar.** { *; }
-keepattributes SourceFile,LineNumberTable
