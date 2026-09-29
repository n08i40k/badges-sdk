-keep class ru.n08i40k.badges.** {
    *;
}

-dontobfuscate
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature

# есть в рантайме ART, но отсутствует в android.jar
-dontwarn sun.misc.Unsafe
