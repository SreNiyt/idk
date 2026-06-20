@BClassName("android.content.res.AssetManager")
public interface AssetManager {
    @BConstructor
    android.content.res.AssetManager _new();

    // Change 'addAssetPath' to 'addAssetPathInternal'
    // Also, some A14 versions require an extra 'boolean' parameter for 'trust'
    @BMethod
    Integer addAssetPathInternal(String path); 

    @BMethod
    Configuration getConfiguration();

    @BMethod
    DisplayMetrics getDisplayMetrics();
}

