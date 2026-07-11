plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("hw_data")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
