package cafe.serenity.tytest

import android.content.res.Configuration
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.serenity.tytest.ui.theme.TYTESTTheme
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val snackbarHostState = remember { SnackbarHostState() }

    TYTESTTheme {
        // A surface container using the 'background' color from the theme
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Graph data")
                    },
                    actions = {
                        IconButton(onClick = { viewModel.handleEvent(GraphViewModel.Event.Refresh) }) {
                            Icon(Icons.Filled.Refresh, "Trigger Refresh")
                        }
                    }
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) {
            val scope = rememberCoroutineScope()
            val state by viewModel.state.collectAsState()
            when (LocalConfiguration.current.orientation) {
                Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_UNDEFINED -> Portrait(
                    it,
                    state,
                    viewModel::handleEvent
                )

                Configuration.ORIENTATION_LANDSCAPE -> Landscape(
                    it,
                    state,
                    viewModel::handleEvent
                )

                else -> Text(text = "Unsupported orientation")
            }

            state.graphData.run {
                if (this is GraphViewModel.GraphData.Error) {
                    scope.launch {
                        snackbarHostState.showSnackbar(errorMsg)
                    }
                }
            }
        }
    }
}

@Composable
fun Portrait(
    paddingValues: PaddingValues,
    state: GraphViewModel.State,
    handleEvent: (event: GraphViewModel.Event) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Table(state.graphData)
        Graph(
            modifier = Modifier.fillMaxSize().weight(0.8f),
            state.graphData,
            state.uiState.graphType,
            handleEvent
        )
        Spacer(modifier = Modifier.weight(0.2f))
        CountSlider(state.pointToRequestCount, state.uiState.countInput, handleEvent)
    }
}

@Composable
fun NoDataText() {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .wrapContentHeight(align = Alignment.CenterVertically),
        textAlign = TextAlign.Center,
        text = "No data",
    )
}

@Composable
fun Landscape(
    paddingValues: PaddingValues,
    state: GraphViewModel.State,
    handleEvent: (event: GraphViewModel.Event) -> Unit
) {
    Graph(
        modifier = Modifier.padding(paddingValues),
        graphData = state.graphData,
        graphType = state.uiState.graphType,
        handleEvent = handleEvent
    )
}

@Composable
fun CountSlider(
    count: Int,
    textFieldValue: TextFieldValue,
    handleEvent: (event: GraphViewModel.Event) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .systemGestureExclusion(),
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current

        TextField(
            modifier = Modifier
                .fillMaxWidth(),
            value = textFieldValue,

            onValueChange = { handleEvent(GraphViewModel.Event.CountTextInput(it)) },
            label = { Text("Point count") },
            maxLines = 1,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(onSend = {

                keyboardController?.hide()
                handleEvent(GraphViewModel.Event.CountTextInputDone(textFieldValue.text))
            })
        )

        Slider(
            value = count.toFloat(),
            steps = 9,
            valueRange = 0f..20f,
            onValueChange = { handleEvent(GraphViewModel.Event.CountSliderInput(it)) }
        )
    }
}

@Composable
fun Table(graphData: GraphViewModel.GraphData) {
    val color = when (graphData) {
        is GraphViewModel.GraphData.Idle -> Color.Green
        is GraphViewModel.GraphData.Error -> Color.Red
        is GraphViewModel.GraphData.Loading -> Color.LightGray
        is GraphViewModel.GraphData.Outdated -> Color.DarkGray
    }
    graphData.points?.let { points ->
        LazyRow {
            // Here is the header
            item {
                Column(Modifier.background(Color.Gray)) {
                    TableCell(text = "X", color = Color.LightGray)
                    TableCell(text = "Y", color = Color.LightGray)
                }
            }
            // Here are all the lines of your table.
            items(points) { point ->
                Column(Modifier.fillMaxWidth()) {
                    TableCell(text = "%.1f".format(point.x), color = color)
                    TableCell(text = "%.1f".format(point.y), color = color)
                }
            }
        }
    }
}

@Composable
fun TableCell(
    text: String,
    color: Color
) {
    Text(
        text = text,
        color = color,
        modifier = Modifier
            .border(1.dp, Color.Black)
            .padding(8.dp)
    )
}

@Composable
fun Graph(
    modifier: Modifier,
    graphData: GraphViewModel.GraphData,
    graphType: GraphViewModel.State.UiState.GraphType,
    handleEvent: (event: GraphViewModel.Event) -> Unit
) {
    Box(modifier = modifier) {
        graphData.points.let { points ->
            if (!points.isNullOrEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize().padding(0.dp, 0.dp, 0.dp, 64.dp), onDraw = {

                    this.drawRect(
                        color = Color.LightGray,
                        size = size)

                    val color = when (graphData) {
                        is GraphViewModel.GraphData.Idle -> Color.Green
                        is GraphViewModel.GraphData.Error -> Color.Red
                        is GraphViewModel.GraphData.Loading -> Color.LightGray
                        is GraphViewModel.GraphData.Outdated -> Color.DarkGray
                    }

                    if (graphType == GraphViewModel.State.UiState.GraphType.Sharp) {
                        this.drawPoints(
                            points.map { Offset(it.x, it.y) },
                            pointMode = PointMode.Polygon,
                            color = color
                        )
                    } else {
                        this.drawPath(
                            path = Path().apply {
                                moveTo(points[0].x, points[0].y)

                                (1..points.size - 1).forEach { idx ->
                                    this.cubicTo(
                                        (points[idx].x + points[idx - 1].x) / 2f, points[idx - 1].y,
                                        (points[idx].x + points[idx - 1].x) / 2f, points[idx].y,
                                        points[idx].x, points[idx].y
                                    )
                                }
                            },
                            color = color,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                })

                Button(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    content = {
                        Text(
                            text = when (graphType) {
                                GraphViewModel.State.UiState.GraphType.Smooth -> "Smooth"
                                else -> "Sharp"
                            }
                        )
                    },
                    onClick = {
                        handleEvent(
                            GraphViewModel.Event.SetGraphType(
                                when (graphType) {
                                    GraphViewModel.State.UiState.GraphType.Smooth -> GraphViewModel.State.UiState.GraphType.Sharp
                                    else -> GraphViewModel.State.UiState.GraphType.Smooth
                                }
                            )
                        )
                    })

                if (graphData is GraphViewModel.GraphData.Outdated) {
                    Text(
                        modifier = Modifier
                            .padding(0.dp, 0.dp, 0.dp, 54.dp)
                            .align(Alignment.Center),
                        text = "Outdated",
                        color = Color.DarkGray
                    )
                }
            } else {
                NoDataText()
            }
        }
        if (graphData is GraphViewModel.GraphData.Loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .width(32.dp)
                    .padding(0.dp, 0.dp, 0.dp, 54.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

class GraphViewModel(private val remote: PointRemote) : ViewModel() {
    private val _graphDataFlow: MutableStateFlow<GraphData> =
        MutableStateFlow(GraphData.Idle(null))
    private val _graphTypeFlow: MutableStateFlow<State.UiState.GraphType> =
        MutableStateFlow(State.UiState.GraphType.Smooth)
    private val _countFlow: MutableStateFlow<Int> = MutableStateFlow(10)
    private val _countInputFlow: MutableStateFlow<TextFieldValue> =
        MutableStateFlow(_countFlow.value.toString().let {
            TextFieldValue(text = it, selection = TextRange(0, it.length))
        })

    val state: StateFlow<State> =
        combine(
            _countFlow,
            _graphDataFlow,
            _graphTypeFlow,
            _countInputFlow
        ) { count, graphData, graphType, _countInput ->
            State(
                pointToRequestCount = count,
                graphData = graphData,
                uiState = State.UiState(graphType, _countInput, producer)
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = State.default
        )

    private val producer = CartesianChartModelProducer()

    init {
        viewModelScope.launch {
            _countFlow.collectLatest {
                loadGraphData(it)
            }
        }

        viewModelScope.launch {
            _graphDataFlow.collectLatest { graphData ->
                graphData.points?.let { points ->
                    producer.runTransaction {
                        lineSeries {
                            series(points.map { it.x },
                                points.map { it.y })
                        }
                    }
                }
            }
        }
    }

    fun handleEvent(event: Event) {
        when (event) {
            is Event.CountTextInputDone -> {
                _countFlow.value = when {
                    event.countText.isBlank() -> 0
                    event.countText.toIntOrNull() != null -> event.countText.toInt()
                    else -> {
                        Log.e("GraphViewModel", "Unable to parse ${event.countText}")
                        0
                    }
                }
            }

            is Event.CountTextInput -> {
                _countInputFlow.value = event.textFieldValue
            }

            is Event.CountSliderInput -> {
                event.sliderPosition.toInt().run {
                    _countFlow.value = this
                    _countInputFlow.value = TextFieldValue(
                        text = this.toString(),
                        selection = TextRange(0, this.toString().length)
                    )
                }
            }

            is Event.SetGraphType -> {
                _graphTypeFlow.value = event.graphType
            }

            Event.Refresh -> {
                viewModelScope.launch {
                    loadGraphData(_countFlow.value)
                }
            }
        }
    }

    private suspend fun loadGraphData(count: Int) {
        _graphDataFlow.value = GraphData.Loading(_graphDataFlow.value.points)
        when (val response = remote.getPoints(count)) {
            is PointRemote.Response.Success -> {
                _graphDataFlow.value =
                    GraphData.Idle(response.points.points.sortedBy { it.x }
                        .map { PointF(it.x, it.y) })
            }

            is PointRemote.Response.Failure -> {
                _graphDataFlow.value =
                    GraphData.Error(_graphDataFlow.value.points, response.errorMsg)
            }
        }

        delay(10000L)

        _graphDataFlow.value = GraphData.Outdated(_graphDataFlow.value.points)
    }

    sealed interface Event {
        data class CountTextInput(val textFieldValue: TextFieldValue) : Event
        data class CountTextInputDone(val countText: String) : Event
        data class CountSliderInput(val sliderPosition: Float) : Event
        data class SetGraphType(val graphType: State.UiState.GraphType) : Event
        data object Refresh : Event
    }

    data class State(
        val pointToRequestCount: Int,
        val graphData: GraphData,
        val uiState: UiState
    ) {

        data class UiState(
            val graphType: GraphType,
            val countInput: TextFieldValue,
            val producer: CartesianChartModelProducer?
        ) {
            sealed interface GraphType {
                data object Sharp : GraphType
                data object Smooth : GraphType
            }

            companion object {
                val default = UiState(GraphType.Sharp, TextFieldValue(0.toString()), null)
            }
        }

        companion object {
            val default = State(0, GraphData.Idle(null), UiState.default)
        }
    }

    sealed class GraphData(val points: List<PointF>?) {
        class Loading(currentPoints: List<PointF>?) : GraphData(currentPoints)
        class Idle(currentPoints: List<PointF>?) : GraphData(currentPoints)
        class Error(currentPoints: List<PointF>?, val errorMsg: String) :
            GraphData(currentPoints)

        class Outdated(currentPoints: List<PointF>?) : GraphData(currentPoints)
    }
}

interface PointRemote {
    suspend fun getPoints(count: Int): Response

    sealed interface Response {
        data class Success(val points: Points) : Response
        data class Failure(val errorMsg: String) : Response
    }
}

object TestPointRemote : PointRemote {
    private val rnd = Random(0)

    override suspend fun getPoints(count: Int): PointRemote.Response {
        delay(5000L)
        return if (true) {
            PointRemote.Response.Success(Points(List(count) {
                GraphPoint(it.toFloat() * 50f, rnd.nextFloat() * 1000f)
            }))
        } else {
            PointRemote.Response.Failure("Unable to load points")
        }
    }
}

class TYPointRemote(private val client: HttpClient) : PointRemote {
    override suspend fun getPoints(count: Int): PointRemote.Response {
        return if (count in RANGE) {
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

    companion object {
        private val RANGE = 1..20
        private const val BASE_URL = "https://hr-challenge.dev.tapyou.com/api"
        private const val POINTS_PATH = "test/points"
    }
}

@Serializable
data class Points(@SerialName("points") val points: List<GraphPoint>)

@Serializable
data class GraphPoint(@SerialName("x") val x: Float, @SerialName("y") val y: Float)


