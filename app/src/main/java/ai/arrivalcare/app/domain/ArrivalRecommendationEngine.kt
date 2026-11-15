package ai.arrivalcare.app.domain

enum class Preference(val label:String){SHORT_WALK("짧은 보행"),STEP_FREE("계단 없는 동선"),ESCORT("보호자 동행 필요")}
data class GeoPoint(val latitude:Double,val longitude:Double){val valid get()=latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0}
data class RouteTarget(val point:GeoPoint,val poiId:String,val address:String,val verified:Boolean=false,val simulated:Boolean=true)
data class Venue(val id:String,val name:String,val address:String,val simulated:Boolean,val center:GeoPoint,val source:String,val facts:String)
object VenueRepository{
    val all=listOf(
        Venue("hospital","가상 A병원","가족의 도착을 위한 시연 공간",true,GeoPoint(37.5665,126.9780),"","실제 시설이 아닌 가상 D/P/M 시나리오"),
        Venue("mall","가상 B쇼핑몰","함께 이동하는 쇼핑 나들이",true,GeoPoint(37.5665,126.9780),"","실제 시설이 아닌 가상 D/P/M 시나리오"),
        Venue("asan","서울아산병원","서울 송파구 올림픽로43길 88",false,GeoPoint(37.526922,127.108381),
            "https://www.amc.seoul.kr/asan/hospitalguide/location/location.do?by=subway3",
            "공식 주소·주차 안내 확인 · 2026-11-15\n중앙주차장·신관/동관 주차 관련 시설은 공식 안내에 기재돼 있어요.\n승하차 구역, 주차장 입구 좌표, 무단차 연결, 만남 지점은 추가 확인이 필요해요."))
    fun get(id:String)=all.firstOrNull{it.id==id}?:all.first()
}
data class ArrivalRecommendation(val plan:Plan?,val title:String,val reason:String,val evidence:String,val variant:String){val canStart get()=plan!=null}

/** Rule-based choices. Demo verification flags describe fictional fixtures, never real-world accessibility. */
object ArrivalRecommendationEngine{
    fun recommend(c:Conditions,accessibilityAvailable:Boolean=true):ArrivalRecommendation{
        val venue=VenueRepository.get(c.facilityId)
        val mode=if(!c.caregiver)Mode.TOGETHER else c.requestedMode
        if(!venue.simulated)return ArrivalRecommendation(null,"장소 정보를 더 확인해야 해요",
            "실제 승하차 구역·주차장 입구·만남 장소가 검증되지 않아 도착 계획을 보류했어요.","공식 시설 정보만 표시 · 실제 경로 요청 안 함","unverified")
        val stepFree=Passenger.WHEELCHAIR in c.passengers||Preference.STEP_FREE in c.preferences
        if(stepFree&&!accessibilityAvailable)return ArrivalRecommendation(null,"계단 없는 연결을 확인해 주세요",
            "무단차 연결 정보가 없어 접근 가능한 경로라고 추천할 수 없어요.","정보 부족 · 장애인 전용 주차면 이용 자격과 별개","missing-accessibility")
        val family=Passenger.CHILD in c.passengers||Passenger.STROLLER in c.passengers
        val short=Preference.SHORT_WALK in c.preferences||Passenger.PARENT in c.passengers
        val variant=when{stepFree->"level";family->"family";short->"near";else->"central"}
        val places=when(variant){
            "level"->listOf("D4 · 무단차 연결 입구","P4 · 승강기 연결 주차장","1층 승강기 앞 만남 공간","P4 → 승강기 → 같은 층 만남 공간")
            "family"->listOf("D3 · 가족 동행 입구","P3 · 가족 라운지 주차장","1층 가족 라운지 앞","P3 → 승강기 → 가족 라운지")
            "near"->if(c.facilityId=="mall")listOf("D2 · 동문 승하차 구역","P2 · 동문 주차장","1층 가족 라운지 앞","P2 → 동문 승강기 → 가족 라운지") else listOf("D1 · 정문 승하차 구역","P1 · 동관 주차장","1층 안내데스크 옆 대기공간","P1 → 동관 승강기 → 1층 안내데스크")
            else->listOf("D2 · 중앙 입구","P2 · 중앙 주차장","1층 중앙 로비","P2 → 중앙 출입구 → 중앙 로비")
        }
        val modeReason=when{
            !c.caregiver->"운전자가 유일한 보호자이므로 함께 주차하고 이동해요."
            mode==Mode.TOGETHER->"일행과 함께 이동하도록 하차 단계를 생략했어요."
            else->"별도 보호자가 함께 내릴 수 있어 시연용 승하차 구역을 먼저 안내해요."
        }
        val why=when(variant){"level"->"무단차 연결이 있는 것으로 설정한 시연 후보를 골랐어요.";"family"->"아이·유모차와 함께 머물기 좋은 가족 라운지 동선을 골랐어요.";"near"->"짧은 보행을 우선하는 시연 후보를 골랐어요.";else->"일반 동승자의 중앙 로비 동선을 골랐어요."}
        val base=Plan.create(c)
        val facility=Facility(c.facilityId+"-"+variant,venue.name,places[0],places[1],places[2],places[3])
        return ArrivalRecommendation(Plan(c,facility,mode,modeReason,base.mobility),if(mode==Mode.DROP_FIRST)"먼저 내려드리고, 다시 만나요" else "처음부터 끝까지, 함께 이동해요",
            modeReason+" "+why,"가상 시설·가상 접근성 조건 · 실제 이용 전 현장 확인",variant)
    }
}

object LiveRoutePolicy{
    fun rejection(target:RouteTarget?,permission:Boolean,connected:Boolean):String?=when{
        target==null||!target.verified||target.simulated||!target.point.valid||target.poiId.isBlank()->"검증된 실제 목적지 좌표가 없어요"
        !permission->"차량 내비 경로 요청 권한이 없어요"
        !connected->"차량 내비게이션 연결을 확인해 주세요"
        else->null
    }
    fun destinationMatches(target:RouteTarget,point:GeoPoint,poiId:String)=point.valid&&target.poiId==poiId&&
        kotlin.math.abs(target.point.latitude-point.latitude)<0.00001&&kotlin.math.abs(target.point.longitude-point.longitude)<0.00001
}
