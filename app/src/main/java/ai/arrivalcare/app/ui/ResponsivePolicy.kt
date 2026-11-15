package ai.arrivalcare.app.ui

object ResponsivePolicy {
    fun sideBySide(widthDp:Int,heightDp:Int)=widthDp>=720 && widthDp>heightDp
    fun mapHeight(heightDp:Int)= (heightDp*0.32f).toInt().coerceIn(130,260).coerceAtMost((heightDp*.45f).toInt())
}

object MapAvailability {
    fun reason(included:Boolean,abi:String,key:String,gles:Int):String?=when{
        !included->"이 호환 빌드는 샘플 동선도를 사용해요"
        abi !in setOf("arm64-v8a","armeabi-v7a")->"이 기기의 지도 엔진을 지원하지 않아요"
        gles<0x20000->"지도 그래픽 환경을 지원하지 않아요"
        key.isBlank()->"지도 연결 전 · 샘플 동선으로 안내해요"
        else->null
    }
}
