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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.serenity.tytest.ui.theme.TYTESTTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.cartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

class MainActivity : ComponentActivity() {
//    private val viewModel = GraphViewModel(TYPointRemote(HttpClient {
//        install(ContentNegotiation) {
//            json(Json {
//                prettyPrint = true
//                isLenient = true
//                ignoreUnknownKeys = true
//            })
//        }
//
//        install(DefaultRequest) {
//            header(HttpHeaders.ContentType, ContentType.Application.Json)
//        }
//    }))

    private val viewModel = GraphViewModel(TestPointRemote)

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
//                Graph(it)
                Graph(Modifier.padding(16.dp), it)

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
fun Graph(modifier: Modifier, points: List<PointF>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            modelProducer.runTransaction {
                lineSeries { series(points.map { it.x }, points.map { it.y }) }
            }
        }
    }

    ComposeChart1(modelProducer, modifier)
}

@Composable
private fun ComposeChart1(modelProducer: CartesianChartModelProducer, modifier: Modifier) {
    CartesianChartHost(
        chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        remember { LineCartesianLayer.LineFill.single(fill(Color(0xffa485e0))) }
                    )
                )
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis =
            HorizontalAxis.rememberBottom(
                guideline = null,
                itemPlacer = remember { HorizontalAxis.ItemPlacer.segmented() },
            ),
            layerPadding =
            cartesianLayerPadding(scalableStartPadding = 16.dp, scalableEndPadding = 16.dp),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
        zoomState = rememberVicoZoomState(zoomEnabled = false),
    )
}

private const val PERSISTENT_MARKER_X = 7f

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

object TestPointRemote: PointRemote {
    private val rnd = Random(0)

    override suspend fun getPoints(count: Int): PointRemote.Response {
        delay((rnd.nextFloat() * 2000F).toLong())
        return if(true) {
            PointRemote.Response.Success(Points(List(count) {
                GraphPoint(it.toFloat() * 600 / count, rnd.nextFloat() * 1000)
            }))
        } else {
            PointRemote.Response.Failure("Unable to load points")
        }
    }
}


@Serializable
data class Points(@SerialName("points")  val points: List<GraphPoint>)
@Serializable
data class GraphPoint(@SerialName("x")val x: Float, @SerialName("y") val y: Float)


