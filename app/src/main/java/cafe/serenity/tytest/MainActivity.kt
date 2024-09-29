package cafe.serenity.tytest

import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.serenity.tytest.ui.theme.TYTESTTheme
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.Point
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.IntersectionPoint
import co.yml.charts.ui.linechart.model.Line
import co.yml.charts.ui.linechart.model.LineChartData
import co.yml.charts.ui.linechart.model.LinePlotData
import co.yml.charts.ui.linechart.model.LineStyle
import co.yml.charts.ui.linechart.model.LineType
import co.yml.charts.ui.linechart.model.SelectionHighlightPopUp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    private val viewModel = GraphViewModel(TYPointRemote(HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(DefaultRequest) {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }
    }))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GraphScreen(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(viewModel: GraphViewModel) {
    TYTESTTheme {
        // A surface container using the 'background' color from the theme
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Graph data")
                    }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
            ) {
                val state by viewModel.state.collectAsState()
                GraphData(points = state.graphData.currentPoints)
                Spacer(modifier = Modifier.weight(1.0f))
                CountSlider(state.pointToRequestCount, viewModel::handleEvent)
            }
        }
    }
}

@Composable
fun GraphData(points: List<PointF>?){
    points.let {
        if(!it.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                Table(it)
                Graph(it)
            }
        } else {
            Text(
                modifier=Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .wrapContentHeight(align = Alignment.CenterVertically),
                textAlign = TextAlign.Center,
                text = "No data",
            )
        }
    }

}

@Composable
fun CountSlider(count: Int, handleEvents: (event: GraphViewModel.Event) -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp)
    ){
        TextField(
            modifier=Modifier.fillMaxWidth(),
            value = count.toString(),
            onValueChange = { handleEvents(GraphViewModel.Event.CountTextInput(it)) },
            label = { Text("Point count") },
            maxLines = 1,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Send)
            )
        Slider(
            value = count.toFloat(),
            steps = 7,
            valueRange = 0f..100f,
            onValueChange = { handleEvents(GraphViewModel.Event.CountSliderInput(it)) }
        )
    }
}

@Composable
fun Table(points: List<PointF>) {
    LazyRow {
        // Here is the header
        item {
            Column(Modifier.background(Color.Gray)) {
                TableCell(text = "X")
                TableCell(text = "Y")
            }
        }
        // Here are all the lines of your table.
        items(points) {point ->
            Column(Modifier.fillMaxWidth()) {
                TableCell(text = "%.1f".format(point.x))
                TableCell(text = "%.1f".format(point.y))
            }
        }
    }
}
@Composable
fun TableCell(
    text: String,
) {
    Text(
        text = text,
        Modifier
            .border(1.dp, Color.Black)
            .padding(8.dp)
    )
}

@Composable
fun Graph(points: List<PointF>) {

    StraightLinechart(points)
}

@Composable
private fun StraightLinechart(points: List<PointF>) {
    var width by remember { mutableStateOf(0)}

    val xAxisData = AxisData.Builder()
        .axisStepSize(((width - 120) / (LocalDensity.current.density * points.size)).dp)
        .steps(points.size - 1)
        .labelData { it.toString() }
        .axisLabelAngle(20f)
        .labelAndAxisLinePadding(15.dp)
        .axisLabelColor(Color.Blue)
        .axisLineColor(Color.DarkGray)
        .build()
    val yAxisData = AxisData.Builder()
        .steps(10)
        .labelData { it.toString() }
        .labelAndAxisLinePadding(30.dp)
        .axisLabelColor(Color.Blue)
        .axisLineColor(Color.DarkGray)
        .build()
    val data = LineChartData(
        linePlotData = LinePlotData(
            lines = listOf(
                Line(
                    dataPoints = points.map { Point(it.x, it.y) },
                    lineStyle = LineStyle(lineType = LineType.Straight(), color = Color.Blue),
                    intersectionPoint = IntersectionPoint(color = Color.Red),
                    selectionHighlightPopUp = SelectionHighlightPopUp(popUpLabel = { x, y ->
                        val xLabel = "x : ${x.toInt()}} "
                        val yLabel = "y : ${String.format("%.2f", y)}"
                        "$xLabel $yLabel"
                    })
                )
            )
        ),
        xAxisData = xAxisData,
        yAxisData = yAxisData
    )
    LineChart(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged {
                width = it.width
            }
            .height(300.dp),
        lineChartData = data
    )
}

class GraphViewModel(private val remote: PointRemote): ViewModel() {
    private val _graphDataFlow: MutableStateFlow<GraphData> = MutableStateFlow(GraphData.Idle(null))
    private val _countFlow: MutableStateFlow<Int> = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            _countFlow.collectLatest { count ->
                val previousValue = _graphDataFlow.value
                _graphDataFlow.value = GraphData.Loading(previousValue.currentPoints)
                val response = remote.getPoints(count)
                when(response){
                    is PointRemote.Response.Success -> {
                        _graphDataFlow.value = GraphData.Idle(response.points.points.sortedBy { it.x }.map { PointF(it.x, it.y) })
                    }
                    is PointRemote.Response.Failure -> {
                        _graphDataFlow.value = GraphData.Error(previousValue.currentPoints, response.errorMsg)
                    }
                }
            }
        }
    }

    val state : StateFlow<State> = _countFlow.combine(_graphDataFlow){ count, graphData ->
        State(
            pointToRequestCount = count,
            getPointsStatus = 0,
            graphData = graphData,
            uiState = State.UiState.default
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = State.default)

    fun handleEvent(event: Event){
        when(event){
            is Event.CountTextInput -> {
                _countFlow.value = when {
                    event.countText.isNullOrBlank() -> 0
                    event.countText.toIntOrNull() != null -> event.countText.toInt()
                    else -> {
                        Log.e("GraphViewModel", "Unable to parse ${event.countText}")
                        0
                    }
                }
            }
            is Event.CountSliderInput -> {
                _countFlow.value = event.sliderPosition.toInt()
            }
        }
    }

    sealed interface Event {
        data class CountTextInput(val countText: String): Event
        data class CountSliderInput(val sliderPosition: Float): Event
    }

    data class State(
        val pointToRequestCount: Int,
        val getPointsStatus: Int,
        val graphData: GraphData,
        val uiState: UiState){

        data class UiState(
            val graphType: GraphType,
        ) {
            sealed interface GraphType{
                object Sharp: GraphType
                object Smooth: GraphType
            }

            companion object {
                val default = UiState(GraphType.Sharp)
            }
        }

        companion object {
            val default = State(0, 0, GraphData.Idle(null), UiState.default)
        }
    }

    sealed class GraphData(val currentPoints: List<PointF>?) {
        class Loading(currentPoints: List<PointF>?): GraphData(currentPoints)
        class Idle(currentPoints: List<PointF>?): GraphData(currentPoints)
        class Error(currentPoints: List<PointF>?, val errorMsg: String): GraphData(currentPoints)
        class Outdated(currentPoints: List<PointF>?): GraphData(currentPoints)
    }
}

interface PointRemote {
    suspend fun getPoints(count: Int): Response

    sealed interface Response {
        data class Success(val points: Points) : Response
        data class Failure(val errorMsg: String): Response
    }
}

//object TestPointRemote: PointRemote {
//    private val rnd = Random(0)
//
//    override suspend fun getPoints(count: Int): PointRemote.Response {
//        delay((rnd.nextFloat() * 2000F).toLong())
//        return if(true) {
//            PointRemote.Response.Success(Points(List(count) {
//                GraphPoint(it.toFloat(), rnd.nextFloat())
//            }))
//        } else {
//            PointRemote.Response.Failure("Unable to load points")
//        }
//    }
//}


@Serializable
data class Points(@SerialName("points")  val points: List<GraphPoint>)
@Serializable
data class GraphPoint(@SerialName("x")val x: Float, @SerialName("y") val y: Float)

class TYPointRemote(private val client: HttpClient): PointRemote {
    private val RANGE = 1..20
    private val BASE_URL = "https://hr-challenge.dev.tapyou.com/api"
    private val POINTS_PATH = "test/points"

    override suspend fun getPoints(count: Int): PointRemote.Response {
        return if(count in RANGE) {
            val response = client.get("$BASE_URL/$POINTS_PATH?count=$count")
            when (response.status) {
                HttpStatusCode.OK -> {
                    PointRemote.Response.Success(response.body())
                }
                else -> {
                    PointRemote.Response.Failure("Request has failed with error code: ${response.status}")
                }
            }
        } else {
            PointRemote.Response.Failure("Count is out of bounds $RANGE")
        }
    }
}

//class Repository(val remote: PointRemote) {
//    private val _pointsFlow = MutableStateFlow<GraphData>(GraphData.Idle(null))
//
//
//    suspend fun requestPoints(count: Int) {
//        val previousValue = _pointsFlow.value
//        _pointsFlow.value = GraphData.Loading(previousValue.currentPoints)
//        val response = remote.getPoints(count)
//        when(response){
//            is PointRemote.Response.Success -> {
//                _pointsFlow.value = GraphData.Idle(response.points.points.sortedBy { it.x }.map { PointF(it.x, it.y) })
//            }
//            is PointRemote.Response.Failure -> {
//                _pointsFlow.value = GraphData.Error(previousValue.currentPoints, response.errorMsg)
//            }
//        }
//    }
//
//    suspend fun makeOutdated(){
//        delay(5000L)
//        val previousValue = _pointsFlow.value
//        _pointsFlow.value = GraphData.Outdated(previousValue.currentPoints)
//    }
//}



