
package com.example.caffeinbody

import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.caffeinbody.databinding.FragmentHomeBinding
import com.github.mikephil.charting.data.Entry
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import java.lang.Math.E
import java.time.Duration
import java.util.*
import kotlin.collections.ArrayList


class HomeFragment : Fragment() {
    var count = 1

    // TODO: Rename and change types of parameters
    private val dataClient by lazy { Wearable.getDataClient(activity) }
    private val messageClient by lazy { Wearable.getMessageClient(getActivity()) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(getActivity()) }
    private val nodeClient by lazy { Wearable.getNodeClient(getActivity()) }
    private val clientDataViewModel by viewModels<ClientDataViewModel>()
    //getActivity()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUI()
        arguments?.let {

        }

        lateinit var mainActivity: MainActivity
        mainActivity = context as MainActivity
        clientDataViewModel.mainActivity = mainActivity
    }

    private val binding: FragmentHomeBinding by lazy {
        FragmentHomeBinding.inflate(
            layoutInflater
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        //   initRecycler()
        setUI()

        binding.addBeverageBtn.setOnClickListener{
            sendFavorite()
            activity?.let{
            val selectActivity =  DrinkTypeActivity()
            val intent = Intent(context, selectActivity::class.java)
            startActivity(intent)
        }}
        binding.showDetailText.setOnClickListener{
            activity?.let{
                val selectActivity =  DetailActivity()
                val intent = Intent(context, selectActivity::class.java)
                startActivity(intent)
            }
        }
        binding.myPageBtn.setOnClickListener{
            val recommendActivity =  RecommendActivity()
            val intent = Intent(context, recommendActivity::class.java)
            startActivity(intent)
        }
        binding.checkWatchBtn.setOnClickListener{
            startWearableActivity()
            Log.e("폰에서 워치앱 열기 on HomeFrag", "하자!!!")
        }
        return binding.root
    }
///////////리스너 등록/제거 부분
    override fun onResume() {
        super.onResume()

        setUI()

        dataClient.addListener(clientDataViewModel)
        messageClient.addListener(clientDataViewModel)
        capabilityClient.addListener(
            clientDataViewModel,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )

        lifecycleScope.launch {
            try {
                capabilityClient.addLocalCapability(CAMERA_CAPABILITY).await()
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.e(TAG, "Could not add capability: $exception")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(clientDataViewModel)
        messageClient.removeListener(clientDataViewModel)
        capabilityClient.removeListener(clientDataViewModel)

        lifecycleScope.launch {
            // This is a judicious use of NonCancellable.
            // This is asynchronous clean-up, since the capability is no longer available.
            // If we allow this to be cancelled, we may leave the capability in-place for other
            // nodes to see.
            withContext(NonCancellable) {
                try {
                    capabilityClient.removeLocalCapability(CAMERA_CAPABILITY).await()
                } catch (exception: Exception) {
                    Log.e(TAG, "Could not remove capability: $exception")
                }
            }
        }
    }
////////////////워치로 열기 버튼 누르면 워치로 메시지 보내서 앱 열게 함
    private fun startWearableActivity() {
        lifecycleScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()//노드 검색
                Log.e("nodes: ", nodes[0].toString())//노드 검색은 된다
                // Send a message to all nodes in parallel
                nodes.map { node ->//워치에서 메시지 받게
                    async {
                        messageClient.sendMessage(node.id, START_ACTIVITY_PATH, byteArrayOf())
                            .await()
                    }
                }.awaitAll()

                Log.d(TAG, "Starting activity requests sent successfully")
            } catch (cancellationException: CancellationException) {
                Log.e(TAG, "실패1")
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(TAG, "실패2")
            }
        }
    }
    ///////////////즐겨찾기 데이터 보내기(동기화 시점은...?)
    private fun sendFavorite() {
        Log.e("보내짐", "")
        lifecycleScope.launch {
            Log.e("TAG", "안녕")
            try {
                val request = PutDataMapRequest.create(FAVORITE_PATH).apply {
                    dataMap.putString(FAVORITE_KEY, "받은 메시지: " + (++count).toString())
                    //dataMap.putString(FAVORITE_KEY, (++count).toString())//메시지가 변경돼야 전송됨.
                    //dataMap.putStringArrayList(FAVORITE_KEY, 리스트)//즐겨찾기 리스트
                }
                    .asPutDataRequest()
                    .setUrgent()

                val result = dataClient.putDataItem(request).await()
                Log.e(TAG, "DataItem saved: $result")
            } catch (cancellationException: CancellationException) {
                Log.e(TAG, "캔슬됨")
            } catch (exception: Exception) {
                Log.d(TAG, "Saving DataItem failed: $exception")
            }
        }
    }

    private fun setUI(){
        val msg = App.prefs.todayCaf
        binding.intakenCaffeineText.setText(msg.toString())
        App.prefs.dayCaffeine?.let { binding.maximumADayText.setText(it) }

        currentCaffeineCalculate(1f)
        val servingsize = App.prefs.currentcaffeine//내가 하루에 섭취할 수 있는 최대 섭취량..? sensitivity와 currentcaffeine의 용도 차이
        if(servingsize!= null)  binding.AvailableCaffeineText.setText(servingsize)
        else binding.AvailableCaffeineText.setText(App.prefs.sensetivity)
        var percent = 1.0
        if(servingsize!= null) {
            percent = servingsize!!.toDouble() / App.prefs.sensetivity!!.toDouble()
        }
        binding.heart.start()
        binding.heart.waveHeightPercent = (percent)!!.toFloat()
    //  percent?.let { binding.heart.setProgress(it.toInt()) }
    }


    fun currentCaffeineCalculate(sensitivity: Float){
        val basicTime = 5
        var timeI = 0
        var nowTime = getTime()

        var caffeineVolume = App.prefs.todayCaf
        var halfTime = basicTime * (2 - sensitivity)//-> 민감도 반영 반감기 시간

        if (caffeineVolume != 0) {
            var blank = App.prefs.date
            var a = JSONArray(blank)
            var times = ArrayList<Float>()

            var blank2 = App.prefs.todayCafJson
            var a2 = JSONArray(blank2)
            var caffeines = ArrayList<Float>()

            var count = 0
            var currentTime = 0f

            for (i in 0 .. a.length() - 1){//원래는 -1 해야 함
                times.add(a.optString(i).toFloat()/60)
                caffeines.add(a2.optString(i).toFloat())
                Log.e("time", "times: " + times[i]+ " Caffeines: " + caffeines[i] + " " + i + "번째")
            }//여기까지가 입력한 시간과 양 어레이로 가져오기
            times.add(0f)//공백 넣어줌

            //첫번째 점 그려줌
            var caffeineRemain = caffeines[timeI]!!
            currentTime = times[timeI] + (halfTime * count++).toFloat()
            putCurrentCaffeine(caffeineRemain)
            Log.e("time", "첫 점: nowTime: $nowTime, currentTime: $currentTime, caffeineRemain: $caffeineRemain, times[timeI]: " + times[timeI])


            while (nowTime>=currentTime){//날짜가 바뀌는 경우 어떻게 계산할지
                val timeGap2 = nowTime - currentTime
                var caffeineRemain2 = caffeineRemain
                if (timeGap2>=0){
                    caffeineRemain2 = caffeineRemain2- caffeineRemain2/2*(timeGap2/halfTime.toFloat())//시간이 흘러 줄어든 후 남은 카페인 양
                    putCurrentCaffeine(caffeineRemain2)
                    Log.e("time", "nowTime: $nowTime, currentTime: $currentTime, caffeineRemain: $caffeineRemain, caffeineRemain2: $caffeineRemain2")

                }

                if (times[timeI + 1] != 0f){//더 추가된 카페인 데이터가 있으면
                    if (currentTime+ halfTime < times[timeI + 1]){//5시간 뒤보다 후에 추가됐으면
                        currentTime = times[timeI] + (halfTime * count).toFloat()
                        caffeineRemain = caffeineRemain!!/(2.0).toFloat()
                        Log.e("time", "one")
                    }else{//그래프 중간에 끼워야 할 때
                        val timeGap = times[timeI + 1] - currentTime//일수도 있고
                        currentTime = times[timeI + 1]
                        caffeineRemain = caffeineRemain - caffeineRemain/2*(timeGap/halfTime.toFloat()) + caffeines[timeI + 1]//시간이 흘러 줄어든 후 남은 카페인 양

                        timeI++
                        count = 0
                        Log.e("time", "two")
                    }
                }else{//이게 끝일 때
                    currentTime = times[timeI] + (halfTime * count).toFloat()
                    caffeineRemain = caffeineRemain!!/(2.0).toFloat()
                    Log.e("time", "three")
                }
                count++



            }/////여기까지만

        }
    }

    fun putCurrentCaffeine(caffeineRemain: Float){
        //---------------섭취권고량 설정-----------------
        //TODO 소현
        var servingsize = App.prefs.sensetivity?.toDouble()//나의 적정하루 섭취권고량
        if (servingsize != null) {
            if((servingsize - caffeineRemain!!) > 0 ) {
                var current = servingsize - caffeineRemain
                App.prefs.currentcaffeine = "%.2f".format(current)
            }
            else App.prefs.currentcaffeine = "0"
        }
        //----------------------------------------
    }

    fun getTime():Float {
        //시간을 json으로 저장
        val calendar = Calendar.getInstance()
        val date = Date()
        calendar.setTime(date)
        var time = (calendar.time.hours*60 + calendar.time.minutes)/60.toFloat()

        return time
    }


    companion object {
        private const val TAG = "HomeFragment"

        private const val START_ACTIVITY_PATH = "/start-activity3"
        private const val CAMERA_CAPABILITY = "camera"
        private const val FAVORITE_PATH = "/favorite"
        private const val FAVORITE_KEY = "favorite"
    }

}