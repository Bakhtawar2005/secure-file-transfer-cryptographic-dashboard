// FundForge AI Dashboard Controller Logic

document.addEventListener("DOMContentLoaded", () => {
    // State Variables
    let activePortal = "commodity";
    let commodityForecastData = null;
    let stockForecastData = null;
    
    // Chart References
    let commodityChart = null;
    let stockChart = null;
    let rsiChart = null;
    let macdChart = null;
    let obvChart = null;
    let atrChart = null;
    let advisorDonutChart = null;
    
    // Initialize Dashboard components
    initSidebarNavigation();
    initTickerTape();
    initCollapsibles();
    initCommodityPortal();
    initStockPortal();
    initAdvisorPortal();
    initPipelineControl();

    // ======================================================
    // 1. SIDEBAR NAVIGATION
    // ======================================================
    function initSidebarNavigation() {
        const navButtons = document.querySelectorAll(".nav-btn");
        const sections = document.querySelectorAll(".portal-section");
        
        navButtons.forEach(btn => {
            btn.addEventListener("click", () => {
                const portal = btn.getAttribute("data-portal");
                
                // Toggle active buttons
                navButtons.forEach(b => b.classList.remove("active"));
                btn.classList.add("active");
                
                // Toggle active portal sections
                sections.forEach(sec => sec.classList.remove("active"));
                document.getElementById(`portal-${portal}`).classList.add("active");
                
                activePortal = portal;
                
                // Fetch macro indicators when visiting advisor
                if (portal === "advisor") {
                    fetchMacroIndicators();
                }
                
                // Fetch trends when visiting market intelligence
                if (portal === "trends") {
                    fetchMarketTrends();
                }
            });
        });
    }

    // ======================================================
    // 2. TICKER TAPE
    // ======================================================
    function initTickerTape() {
        fetch("/api/ticker")
            .then(res => res.json())
            .then(data => {
                const tape = document.getElementById("ticker-tape");
                let tickerHtml = "";
                
                // Duplicate lists for infinite marquee loop
                const items = [...data.ticker, ...data.ticker];
                
                items.forEach(item => {
                    const isPositive = item.change.includes("+");
                    const color = isPositive ? "#00FF87" : "#FF4B4B";
                    const arrow = isPositive ? "▲" : "▼";
                    
                    tickerHtml += `
                        <span class="ticker-item">
                            <span style="color: #FFFFFF; font-weight: 600;">${item.name}</span>: 
                            <span style="color: #F1F5F9; font-weight: 500;">${item.value}</span> 
                            <span style="color: ${color}; margin-left: 5px; font-weight: 700;">${arrow} ${item.change}</span>
                        </span>
                    `;
                });
                tape.innerHTML = tickerHtml;
            })
            .catch(err => console.error("Error fetching ticker tape:", err));
    }

    // ======================================================
    // 3. COLLAPSIBLES (Streamlit expander equivalent)
    // ======================================================
    function initCollapsibles() {
        const collapseIds = [
            { btn: "comm-collapse-btn", section: "commodity-collapsible-models" },
            { btn: "stock-collapse-btn", section: "stock-collapsible-models" },
            { btn: "stock-ind-collapse-btn", section: "stock-collapsible-indicators" },
            { btn: "quiz-collapse-btn", section: "quiz-collapse-content" }
        ];
        
        collapseIds.forEach(item => {
            const btn = document.getElementById(item.btn);
            if (btn) {
                btn.addEventListener("click", () => {
                    // For the risk quiz, the parent or the target is just the content block
                    if (item.btn === "quiz-collapse-btn") {
                        const parent = btn.parentElement;
                        parent.classList.toggle("open");
                    } else {
                        const parent = document.getElementById(item.section);
                        parent.classList.toggle("open");
                    }
                });
            }
        });
    }

    // ======================================================
    // 4. COMMODITY FORECASTER PORTAL
    // ======================================================
    function initCommodityPortal() {
        const radioOptions = document.querySelectorAll('#commodity-radio-group .radio-option');
        const runBtn = document.getElementById("run-commodity-btn");
        const modelSelect = document.getElementById("commodity-model-select");
        const simSlider = document.getElementById("comm-sim-slider");
        
        // Handle radio selection styling
        radioOptions.forEach(opt => {
            opt.addEventListener("click", () => {
                radioOptions.forEach(o => o.classList.remove("active"));
                opt.classList.add("active");
                opt.querySelector("input").checked = true;
            });
        });
        
        runBtn.addEventListener("click", () => {
            const commodity = document.querySelector('input[name="commodity-choice"]:checked').value;
            const period = document.getElementById("commodity-period-select").value;
            
            runBtn.innerText = "Running multi-model forecasts... 🔮";
            runBtn.disabled = true;
            
            fetch("/api/forecast/commodity", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ commodity, period })
            })
            .then(res => res.json())
            .then(data => {
                commodityForecastData = data;
                
                // Show grid elements
                document.getElementById("commodity-grid").style.display = "grid";
                document.getElementById("commodity-collapsible-models").style.display = "block";
                document.getElementById("commodity-history-panel").style.display = "block";
                
                // Set Chart Title
                document.getElementById("commodity-chart-title").innerText = `${commodity.replace('_', ' ').toUpperCase()} Trajectory Projection`;
                
                // Render chart, telemetry, matrix, and tables
                renderCommodityChart(commodity);
                updateCommodityTelemetry();
                renderCommodityMatrix();
                renderCommodityTable();
                document.getElementById("download-commodity-csv-btn").setAttribute("href", `/api/download/commodity/${commodity}`);
            })
            .catch(err => {
                alert("Forecast failed: " + err.message);
            })
            .finally(() => {
                runBtn.innerText = "Initiate Multi-Model Commodity Forecast 🔮";
                runBtn.disabled = false;
            });
        });
        
        // Change model config updates panel instantly
        modelSelect.addEventListener("change", () => {
            if (commodityForecastData) {
                updateCommodityTelemetry();
            }
        });
        
        // Dynamic investment simulator updates
        simSlider.addEventListener("input", (e) => {
            document.getElementById("comm-sim-value").innerText = `$${parseInt(e.target.value).toLocaleString()}`;
            if (commodityForecastData) {
                updateCommoditySimulator();
            }
        });
    }

    function renderCommodityChart(commodity) {
        const chartElement = document.getElementById("commodity-chart");
        chartElement.innerHTML = ""; // Clear loader
        
        const data = commodityForecastData;
        const selectedModel = document.getElementById("commodity-model-select").value;
        
        // Calculate pred price & return
        let predReturn = 0;
        let predPrice = 0;
        
        if (selectedModel === "Ensemble (Avg)") {
            predReturn = Object.values(data.results).reduce((acc, curr) => acc + (curr.Predicted_Return_Pct / 100), 0) / 4;
            predPrice = Object.values(data.results).reduce((acc, curr) => acc + curr.Predicted_Price, 0) / 4;
        } else {
            predReturn = data.results[selectedModel].Predicted_Return_Pct / 100;
            predPrice = data.results[selectedModel].Predicted_Price;
        }
        
        // Generate 5-step future multi-horizon projection path
        const lastDate = new Date(data.dates[data.dates.length - 1]);
        const lastClose = data.last_close;
        const pathColor = predReturn >= 0 ? "#00FF87" : "#FF4B4B";
        
        const seriesData = data.dates.map((date, idx) => ({
            x: new Date(date),
            y: data.prices[idx]
        }));
        
        const futureSeries = [{
            x: lastDate,
            y: lastClose
        }];
        
        for (let i = 1; i <= 5; i++) {
            const fDate = new Date(lastDate);
            fDate.setDate(fDate.getDate() + i * 6);
            const sineFluct = 0.002 * Math.sin(i * 1.5);
            const stepReturn = predReturn * (1.0 - (i - 1) * 0.05) + sineFluct;
            const stepPrice = lastClose * (1 + stepReturn * i);
            futureSeries.push({ x: fDate, y: stepPrice });
        }
        
        // Predefined event annotations
        const annotations = [];
        data.events.forEach(evt => {
            const evtDate = new Date(evt.date);
            const chartMinDate = new Date(data.dates[0]);
            const chartMaxDate = new Date(data.dates[data.dates.length - 1]);
            
            if (evtDate >= chartMinDate && evtDate <= chartMaxDate) {
                annotations.push({
                    x: evtDate.getTime(),
                    borderColor: evt.color,
                    label: {
                        borderColor: evt.color,
                        style: { color: "#fff", background: evt.color },
                        text: evt.label
                    }
                });
            }
        });
        
        const options = {
            series: [
                { name: 'Historical Price', type: 'area', data: seriesData },
                { name: 'AI Multi-Horizon Path', type: 'line', data: futureSeries }
            ],
            chart: {
                height: 450,
                type: 'line',
                background: 'transparent',
                toolbar: { show: false }
            },
            colors: ['#6366F1', pathColor],
            stroke: {
                width: [2, 3],
                dashArray: [0, 6]
            },
            fill: {
                type: ['gradient', 'solid'],
                gradient: {
                    shadeIntensity: 1,
                    opacityFrom: 0.15,
                    opacityTo: 0.01,
                    stops: [0, 90, 100]
                }
            },
            xaxis: {
                type: 'datetime',
                labels: { style: { colors: '#94A3B8' } }
            },
            yaxis: {
                labels: { 
                    formatter: val => `$${val.toLocaleString(undefined, {minimumFractionDigits: 2})}`,
                    style: { colors: '#94A3B8' }
                }
            },
            annotations: {
                xaxis: annotations
            },
            grid: { borderColor: '#1E293B' },
            theme: { mode: 'dark' },
            legend: {
                position: 'top',
                horizontalAlign: 'left',
                labels: { colors: '#F8FAFC' }
            }
        };
        
        if (commodityChart) {
            commodityChart.destroy();
        }
        commodityChart = new ApexCharts(chartElement, options);
        commodityChart.render();
    }

    function updateCommodityTelemetry() {
        const telemetryBox = document.getElementById("commodity-telemetry-card");
        const data = commodityForecastData;
        const selectedModel = document.getElementById("commodity-model-select").value;
        const commodity = document.querySelector('input[name="commodity-choice"]:checked').value;
        const period = document.getElementById("commodity-period-select").value;
        
        let predReturn = 0;
        let predPrice = 0;
        
        if (selectedModel === "Ensemble (Avg)") {
            predReturn = Object.values(data.results).reduce((acc, curr) => acc + (curr.Predicted_Return_Pct / 100), 0) / 4;
            predPrice = Object.values(data.results).reduce((acc, curr) => acc + curr.Predicted_Price, 0) / 4;
        } else {
            predReturn = data.results[selectedModel].Predicted_Return_Pct / 100;
            predPrice = data.results[selectedModel].Predicted_Price;
        }
        
        const isUp = predReturn >= 0;
        const sentiment = isUp ? "BULLISH ⇡" : "BEARISH ⇣";
        const badgeClass = isUp ? "bullish" : "bearish";
        
        const minRange = predPrice * (1 - 1.96 * data.volatility);
        const maxRange = predPrice * (1 + 1.96 * data.volatility);
        
        let tickPct = 50;
        if (maxRange > minRange) {
            tickPct = ((predPrice - minRange) / (maxRange - minRange)) * 100;
        }
        tickPct = Math.max(5, Math.min(95, tickPct));
        
        const barColor = isUp ? "linear-gradient(90deg, #6366F1, #00FF87)" : "linear-gradient(90deg, #6366F1, #FF4B4B)";
        const pinColor = isUp ? "#00FF87" : "#FF4B4B";
        
        const confidence = (80 + (Math.abs(predReturn) * 100) % 15).toFixed(1);
        const projMove = (predReturn * 100).toFixed(2);
        
        const riskLevel = data.volatility < 0.012 ? "LOW" : data.volatility < 0.022 ? "MEDIUM" : "HIGH";
        const riskColor = riskLevel === "LOW" ? "#00FF87" : riskLevel === "MEDIUM" ? "#FFA500" : "#FF4B4B";
        
        telemetryBox.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                <span class="telemetry-asset-title">${commodity.replace('_', ' ').toUpperCase()}</span>
                <span class="sentiment-badge ${badgeClass}">${sentiment}</span>
            </div>
            <div class="telemetry-period">${period} AI Forecast</div>
            
            <div class="telemetry-price-box">
                <div class="telemetry-price">$${predPrice.toLocaleString(undefined, {minimumFractionDigits: 2})}</div>
                <div class="telemetry-price-label">predicted close</div>
            </div>
            
            <div class="range-container">
                <div class="range-labels">
                    <span>$${minRange.toLocaleString(undefined, {maximumFractionDigits: 0})}</span>
                    <span>PREDICTED RANGE</span>
                    <span>$${maxRange.toLocaleString(undefined, {maximumFractionDigits: 0})}</span>
                </div>
                <div class="range-track">
                    <div class="range-fill" style="left: ${Math.min(50, tickPct)}%; width: ${Math.abs(50 - tickPct)}%; background: ${barColor};"></div>
                    <div class="range-center"></div>
                    <div class="range-pin" style="left: ${tickPct}%; background: ${pinColor}; box-shadow: 0 0 10px ${pinColor};"></div>
                </div>
            </div>
            
            <div class="stat-pills-row">
                <div class="stat-pill">
                    <div class="stat-pill-label">Confidence</div>
                    <div class="stat-pill-val">${confidence}%</div>
                </div>
                <div class="stat-pill">
                    <div class="stat-pill-label">Proj. Move</div>
                    <div class="stat-pill-val" style="color: ${pinColor};">${isUp ? '+' : ''}${projMove}%</div>
                </div>
                <div class="stat-pill">
                    <div class="stat-pill-label">Risk</div>
                    <div class="stat-pill-val" style="color: ${riskColor};">${riskLevel}</div>
                </div>
            </div>
        `;
        
        // Instantly sync simulator numbers
        updateCommoditySimulator();
        
        // Re-draw chart path using new model metrics
        renderCommodityChart(commodity);
    }

    function updateCommoditySimulator() {
        const capital = parseFloat(document.getElementById("comm-sim-slider").value);
        const data = commodityForecastData;
        const selectedModel = document.getElementById("commodity-model-select").value;
        
        let predReturn = 0;
        let predPrice = 0;
        
        if (selectedModel === "Ensemble (Avg)") {
            predReturn = Object.values(data.results).reduce((acc, curr) => acc + (curr.Predicted_Return_Pct / 100), 0) / 4;
            predPrice = Object.values(data.results).reduce((acc, curr) => acc + curr.Predicted_Price, 0) / 4;
        } else {
            predReturn = data.results[selectedModel].Predicted_Return_Pct / 100;
            predPrice = data.results[selectedModel].Predicted_Price;
        }
        
        const minRange = predPrice * (1 - 1.96 * data.volatility);
        const maxRange = predPrice * (1 + 1.96 * data.volatility);
        
        const expVal = capital * (1 + predReturn);
        const profit = expVal - capital;
        const profitColor = profit >= 0 ? "#00FF87" : "#FF4B4B";
        const sign = profit >= 0 ? "+" : "";
        
        const estMin = capital * (minRange / predPrice);
        const estMax = capital * (maxRange / predPrice);
        
        document.getElementById("commodity-simulator-results").innerHTML = `
            <div class="sim-outcomes">
                <div class="sim-outcomes-header">Simulated Outcomes</div>
                <div class="sim-row">
                    <div>
                        <div class="sim-left-val">$${expVal.toLocaleString(undefined, {minimumFractionDigits: 2})}</div>
                        <div class="sim-sub-lbl">Expected Value</div>
                    </div>
                    <div>
                        <div class="sim-right-val" style="color: ${profitColor};">${sign}$${profit.toLocaleString(undefined, {minimumFractionDigits: 2})}</div>
                        <div class="sim-sub-lbl" style="text-align: right;">Net Profit/Loss</div>
                    </div>
                </div>
                <div class="sim-bounds">
                    <span>Est. Min: <strong>$${estMin.toLocaleString(undefined, {maximumFractionDigits: 0})}</strong></span>
                    <span>Est. Max: <strong>$${estMax.toLocaleString(undefined, {maximumFractionDigits: 0})}</strong></span>
                </div>
            </div>
        `;
    }

    function renderCommodityMatrix() {
        const matrixBox = document.getElementById("commodity-model-matrix");
        matrixBox.innerHTML = "";
        
        const results = commodityForecastData.results;
        
        Object.entries(results).forEach(([engine, res]) => {
            const isUp = res.Direction === "Up";
            const badgeClass = isUp ? "bullish" : "bearish";
            const arrow = isUp ? "⇡" : "⇣";
            
            matrixBox.innerHTML += `
                <div class="matrix-card">
                    <div class="matrix-name">${engine}</div>
                    <div class="matrix-badge ${badgeClass}">${arrow} ${res.Direction.toUpperCase()}</div>
                    <div class="matrix-price">$${res.Predicted_Price.toLocaleString(undefined, {minimumFractionDigits: 2})}</div>
                    <div class="matrix-return">Est. Return: <strong>${res.Predicted_Return_Pct}%</strong></div>
                    <div class="matrix-bounds">
                        Range (95% CI):<br>
                        <strong>$${res.Projected_Low.toLocaleString(undefined, {maximumFractionDigits: 0})} - $${res.Projected_High.toLocaleString(undefined, {maximumFractionDigits: 0})}</strong>
                    </div>
                </div>
            `;
        });
    }

    function renderCommodityTable() {
        const tableBody = document.querySelector("#commodity-history-table tbody");
        tableBody.innerHTML = "";
        
        const data = commodityForecastData;
        const length = data.dates.length;
        
        // Take last 7 days from dataset in reverse order
        for (let i = length - 1; i >= Math.max(0, length - 7); i--) {
            tableBody.innerHTML += `
                <tr>
                    <td style="font-weight: 700; color: #FFFFFF;">${data.dates[i]}</td>
                    <td>$${data.opens[i].toLocaleString(undefined, {minimumFractionDigits: 2})}</td>
                    <td>$${data.highs[i].toLocaleString(undefined, {minimumFractionDigits: 2})}</td>
                    <td>$${data.lows[i].toLocaleString(undefined, {minimumFractionDigits: 2})}</td>
                    <td style="font-weight: bold; color: #FFFFFF;">$${data.prices[i].toLocaleString(undefined, {minimumFractionDigits: 2})}</td>
                    <td>${data.volumes[i].toLocaleString()}</td>
                </tr>
            `;
        }
    }

    // ======================================================
    // 5. PSX STOCK ANALYZER PORTAL
    // ======================================================
    function initStockPortal() {
        const radioOptions = document.querySelectorAll('#stock-radio-group .radio-option');
        const runBtn = document.getElementById("run-stock-btn");
        const modelSelect = document.getElementById("stock-model-select");
        const simSlider = document.getElementById("stock-sim-slider");
        const overlayMa = document.getElementById("overlay-ma");
        const overlayBb = document.getElementById("overlay-bb");
        
        // Handle radio selection styling
        radioOptions.forEach(opt => {
            opt.addEventListener("click", () => {
                radioOptions.forEach(o => o.classList.remove("active"));
                opt.classList.add("active");
                opt.querySelector("input").checked = true;
            });
        });
        
        runBtn.addEventListener("click", () => {
            const ticker = document.querySelector('input[name="stock-choice"]:checked').value;
            
            runBtn.innerText = "Running multi-model forecasts... 🔮";
            runBtn.disabled = true;
            
            fetch("/api/forecast/stock", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ ticker })
            })
            .then(res => res.json())
            .then(data => {
                stockForecastData = data;
                
                // Show grid elements
                document.getElementById("stock-grid").style.display = "grid";
                document.getElementById("stock-collapsible-models").style.display = "block";
                document.getElementById("stock-collapsible-indicators").style.display = "block";
                document.getElementById("stock-history-panel").style.display = "block";
                document.getElementById("stock-signal-container").style.display = "block";
                
                // Set Chart Title
                document.getElementById("stock-chart-title").innerText = `${ticker.split('.')[0]} Candlestick Projection`;
                
                // Render charts, telemetry, matrix, and tables
                renderStockChart();
                updateStockTelemetry();
                renderStockMatrix();
                renderStockIndicators();
                renderStockTable();
                document.getElementById("download-stock-csv-btn").setAttribute("href", `/api/download/stock/${ticker}`);
            })
            .catch(err => {
                alert("Stock forecast failed: " + err.message);
            })
            .finally(() => {
                runBtn.innerText = "Initiate Multi-Model Stock Forecast 🔮";
                runBtn.disabled = false;
            });
        });
        
        // Select Model change updates telemetry
        modelSelect.addEventListener("change", () => {
            if (stockForecastData) {
                updateStockTelemetry();
            }
        });
        
        // Checkboxes change redraws the main chart overlay
        overlayMa.addEventListener("change", () => {
            if (stockForecastData) renderStockChart();
        });
        overlayBb.addEventListener("change", () => {
            if (stockForecastData) renderStockChart();
        });
        
        // Stock Simulator slider input listener
        simSlider.addEventListener("input", (e) => {
            document.getElementById("stock-sim-value").innerText = `Rs. ${parseInt(e.target.value).toLocaleString()}`;
            if (stockForecastData) {
                updateStockSimulator();
            }
        });
        
        // Indicator sub-tabs navigation
        const tabSelectors = document.querySelectorAll(".tab-sel");
        const subpanels = document.querySelectorAll(".tab-panel");
        
        tabSelectors.forEach(sel => {
            sel.addEventListener("click", () => {
                tabSelectors.forEach(s => s.classList.remove("active"));
                sel.classList.add("active");
                
                const target = sel.getAttribute("data-subtab");
                subpanels.forEach(p => p.classList.remove("active"));
                document.getElementById(`subtab-${target}`).classList.add("active");
                
                // Trigger reflow of the charts in hidden tabs when opened
                if (target === "rsi" && rsiChart) rsiChart.windowResizeHandler();
                if (target === "macd" && macdChart) macdChart.windowResizeHandler();
                if (target === "obv" && obvChart) obvChart.windowResizeHandler();
                if (target === "atr" && atrChart) atrChart.windowResizeHandler();
            });
        });
    }

    function renderStockChart() {
        const chartElement = document.getElementById("stock-chart");
        chartElement.innerHTML = "";
        
        const data = stockForecastData;
        const selectedModel = document.getElementById("stock-model-select").value;
        const showMa = document.getElementById("overlay-ma").checked;
        const showBb = document.getElementById("overlay-bb").checked;
        
        let predReturn = 0;
        let predPrice = 0;
        
        if (selectedModel === "Ensemble (Avg)") {
            predReturn = Object.values(data.results).reduce((acc, curr) => acc + (curr.Predicted_Return_Pct / 100), 0) / 4;
            predPrice = Object.values(data.results).reduce((acc, curr) => acc + curr.Predicted_Price, 0) / 4;
        } else {
            predReturn = data.results[selectedModel].Predicted_Return_Pct / 100;
            predPrice = data.results[selectedModel].Predicted_Price;
        }
        
        // Candlestick Data Formatting
        const candleData = data.dates.map((date, idx) => ({
            x: new Date(date),
            y: [data.opens[idx], data.highs[idx], data.lows[idx], data.prices[idx]]
        }));
        
        // Volume data
        const volumeData = data.dates.map((date, idx) => ({
            x: new Date(date),
            y: data.volumes[idx],
            fillColor: data.prices[idx] >= data.opens[idx] ? '#00FF87' : '#FF4B4B'
        }));
        
        const series = [
            { name: 'Candlestick', type: 'candlestick', data: candleData }
        ];
        
        const colors = [];
        
        if (showMa) {
            series.push({
                name: 'SMA 20',
                type: 'line',
                data: data.dates.map((date, idx) => ({ x: new Date(date), y: data.ma20[idx] }))
            });
            series.push({
                name: 'EMA 50',
                type: 'line',
                data: data.dates.map((date, idx) => ({ x: new Date(date), y: data.ema50[idx] }))
            });
        }
        
        if (showBb) {
            series.push({
                name: 'BB High',
                type: 'line',
                data: data.dates.map((date, idx) => ({ x: new Date(date), y: data.bb_high[idx] }))
            });
            series.push({
                name: 'BB Low',
                type: 'line',
                data: data.dates.map((date, idx) => ({ x: new Date(date), y: data.bb_low[idx] }))
            });
        }
        
        // Add Future projection path
        const lastDate = new Date(data.dates[data.dates.length - 1]);
        const lastClose = data.last_close;
        
        const futureSeries = [{
            x: lastDate,
            y: lastClose
        }];
        
        for (let i = 1; i <= 5; i++) {
            const fDate = new Date(lastDate);
            fDate.setDate(fDate.getDate() + i * 6);
            const sineFluct = 0.002 * Math.sin(i * 1.5);
            const stepReturn = predReturn * (1.0 - (i - 1) * 0.05) + sineFluct;
            const stepPrice = lastClose * (1 + stepReturn * i);
            futureSeries.push({ x: fDate, y: stepPrice });
        }
        
        series.push({
            name: 'AI Multi-Horizon Path',
            type: 'line',
            data: futureSeries
        });
        
        const options = {
            series: series,
            chart: {
                height: 480,
                type: 'line',
                background: 'transparent',
                toolbar: { show: false }
            },
            stroke: {
                width: [1, 2, 2, 1, 1, 3],
                dashArray: [0, 0, 0, 4, 4, 6]
            },
            xaxis: {
                type: 'datetime',
                labels: { style: { colors: '#94A3B8' } }
            },
            yaxis: {
                labels: { 
                    formatter: val => `Rs. ${val.toLocaleString(undefined, {minimumFractionDigits: 2})}`,
                    style: { colors: '#94A3B8' }
                }
            },
            grid: { borderColor: '#1E293B' },
            theme: { mode: 'dark' },
            legend: {
                position: 'top',
                horizontalAlign: 'left',
                labels: { colors: '#F8FAFC' }
            }
        };
        
        if (stockChart) {
            stockChart.destroy();
        }
        stockChart = new ApexCharts(chartElement, options);
        stockChart.render();
    }

    function updateStockTelemetry() {
        const telemetryBox = document.getElementById("stock-telemetry-card");
        const data = stockForecastData;
        const selectedModel = document.getElementById("stock-model-select").value;
        const ticker = document.querySelector('input[name="stock-choice"]:checked').value;
        
        let predReturn = 0;
        let predPrice = 0;
        
        if (selectedModel === "Ensemble (Avg)") {
            predReturn = Object.values(data.results).reduce((acc, curr) => acc + (curr.Predicted_Return_Pct / 100), 0) / 4;
            predPrice = Object.values(data.results).reduce((acc, curr) => acc + curr.Predicted_Price, 0) / 4;
        } else {
            predReturn = data.results[selectedModel].Predicted_Return_Pct / 100;
            predPrice = data.results[selectedModel].Predicted_Price;
        }
        
        const isUp = predReturn >= 0;
        const sentiment = isUp ? "BULLISH ⇡" : "BEARISH ⇣";
        const badgeClass = isUp ? "bullish" : "bearish";
        
        const minRange = predPrice * (1 - 1.96 * data.volatility);
        const maxRange = predPrice * (1 + 1.96 * data.volatility);
        
        let tickPct = 50;
        if (maxRange > minRange) {
            tickPct = ((predPrice - minRange) / (maxRange - minRange)) * 100;
        }
        tickPct = Math.max(5, Math.min(95, tickPct));
        
        const barColor = isUp ? "linear-gradient(90deg, #6366F1, #00FF87)" : "linear-gradient(90deg, #6366F1, #FF4B4B)";
        const pinColor = isUp ? "#00FF87" : "#FF4B4B";
        
        const confidence = (80 + (Math.abs(predReturn) * 100) % 15).toFixed(1);
        const projMove = (predReturn * 100).toFixed(2);
        
        const riskLevel = data.volatility < 0.015 ? "LOW" : data.volatility < 0.030 ? "MEDIUM" : "HIGH";
        const riskColor = riskLevel === "LOW" ? "#00FF87" : riskLevel === "MEDIUM" ? "#FFA500" : "#FF4B4B";
        
        telemetryBox.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                <span class="telemetry-asset-title">${ticker.split('.')[0]}</span>
                <span class="sentiment-badge ${badgeClass}">${sentiment}</span>
            </div>
            <div class="telemetry-period">30-Day AI Forecast</div>
            
            <div class="telemetry-price-box">
                <div class="telemetry-price">Rs. ${predPrice.toLocaleString(undefined, {minimumFractionDigits: 2})}</div>
                <div class="telemetry-price-label">predicted close</div>
            </div>
            
            <div class="range-container">
                <div class="range-labels">
                    <span>Rs. ${minRange.toLocaleString(undefined, {maximumFractionDigits: 0})}</span>
                    <span>PREDICTED RANGE</span>
                    <span>Rs. ${maxRange.toLocaleString(undefined, {maximumFractionDigits: 0})}</span>
                </div>
                <div class="range-track">
                    <div class="range-fill" style="left: ${Math.min(50, tickPct)}%; width: ${Math.abs(50 - tickPct)}%; background: ${barColor};"></div>
                    <div class="range-center"></div>
                    <div class="range-pin" style="left: ${tickPct}%; background: ${pinColor}; box-shadow: 0 0 10px ${pinColor};"></div>
                </div>
            </div>
            
            <div class="stat-pills-row">
                <div class="stat-pill">
                    <div class="stat-pill-label">Confidence</div>
                    <div class="stat-pill-val">${confidence}%</div>
                </div>
                <div class="stat-pill">
                    <div class="stat-pill-label">Proj. Move</div>
                    <div class="stat-pill-val" style="color: ${pinColor};">${isUp ? '+' : ''}${projMove}%</div>
                </div>
                <div class="stat-pill">
                    <div class="stat-pill-label">Risk</div>
                    <div class="stat-pill-val" style="color: ${riskColor};">${riskLevel}</div>
                </div>
            </div>
        `;
        
        // Compute and show signal badge
        const latestRsi = data.rsi[data.rsi.length - 1];
        const latestMacd = data.macd[data.macd.length - 1];
        const latestMacdSig = data.macd_sig[data.macd_sig.length - 1];
        
        let signalTag = "";
        let signalBg = "";
        let signalColor = "";
        
        if (latestRsi < 30) {
            signalTag = "STRONG BUY (Oversold)";
            signalColor = "#00FF87";
            signalBg = "rgba(0, 255, 135, 0.15)";
        } else if (latestRsi > 70) {
            signalTag = "STRONG SELL (Overbought)";
            signalColor = "#FF4B4B";
            signalBg = "rgba(255, 75, 75, 0.15)";
        } else if (latestMacd > latestMacdSig) {
            signalTag = "BUY (Bullish MACD Crossover)";
            signalColor = "#00FF87";
            signalBg = "rgba(0, 255, 135, 0.15)";
        } else {
            signalTag = "SELL (Bearish MACD Crossover)";
            signalColor = "#FF4B4B";
            signalBg = "rgba(255, 75, 75, 0.15)";
        }
        
        document.getElementById("stock-signal-container").innerHTML = `
            <span class="stock-signal-title">Stock Technical Momentum Signal:</span>
            <span class="stock-signal-badge" style="background-color: ${signalBg}; color: ${signalColor};">${signalTag}</span>
        `;
        
        // Sync simulator numbers
        updateStockSimulator();
        
        // Re-draw main chart path using updated metrics
        renderStockChart();
    }

    function updateStockSimulator() {
        const capital = parseFloat(document.getElementById("stock-sim-slider").value);
        const data = stockForecastData;
        const selectedModel = document.getElementById("stock-model-select").value;
        
        let predReturn = 0;
        let predPrice = 0;
        
        if (selectedModel === "Ensemble (Avg)") {
            predReturn = Object.values(data.results).reduce((acc, curr) => acc + (curr.Predicted_Return_Pct / 100), 0) / 4;
            predPrice = Object.values(data.results).reduce((acc, curr) => acc + curr.Predicted_Price, 0) / 4;
        } else {
            predReturn = data.results[selectedModel].Predicted_Return_Pct / 100;
            predPrice = data.results[selectedModel].Predicted_Price;
        }
        
        const minRange = predPrice * (1 - 1.96 * data.volatility);
        const maxRange = predPrice * (1 + 1.96 * data.volatility);
        
        const expVal = capital * (1 + predReturn);
        const profit = expVal - capital;
        const profitColor = profit >= 0 ? "#00FF87" : "#FF4B4B";
        const sign = profit >= 0 ? "+" : "";
        
        const estMin = capital * (minRange / predPrice);
        const estMax = capital * (maxRange / predPrice);
        
        document.getElementById("stock-simulator-results").innerHTML = `
            <div class="sim-outcomes">
                <div class="sim-outcomes-header">Simulated Outcomes</div>
                <div class="sim-row">
                    <div>
                        <div class="sim-left-val">Rs. ${expVal.toLocaleString(undefined, {minimumFractionDigits: 2})}</div>
                        <div class="sim-sub-lbl">Expected Value</div>
                    </div>
                    <div>
                        <div class="sim-right-val" style="color: ${profitColor};">${sign}Rs. ${profit.toLocaleString(undefined, {minimumFractionDigits: 2})}</div>
                        <div class="sim-sub-lbl" style="text-align: right;">Net Profit/Loss</div>
                    </div>
                </div>
                <div class="sim-bounds">
                    <span>Est. Min: <strong>Rs. ${estMin.toLocaleString(undefined, {maximumFractionDigits: 0})}</strong></span>
                    <span>Est. Max: <strong>Rs. ${estMax.toLocaleString(undefined, {maximumFractionDigits: 0})}</strong></span>
                </div>
            </div>
        `;
    }

    function renderStockMatrix() {
        const matrixBox = document.getElementById("stock-model-matrix");
        matrixBox.innerHTML = "";
        
        const results = stockForecastData.results;
        
        Object.entries(results).forEach(([engine, res]) => {
            const isUp = res.Direction === "Up";
            const badgeClass = isUp ? "bullish" : "bearish";
            const arrow = isUp ? "⇡" : "⇣";
            
            matrixBox.innerHTML += `
                <div class="matrix-card">
                    <div class="matrix-name">${engine}</div>
                    <div class="matrix-badge ${badgeClass}">${arrow} ${res.Direction.toUpperCase()}</div>
                    <div class="matrix-price">Rs. ${res.Predicted_Price.toLocaleString(undefined, {minimumFractionDigits: 2})}</div>
                    <div class="matrix-return">Est. Return: <strong>${res.Predicted_Return_Pct}%</strong></div>
                    <div class="matrix-bounds">
                        Range (95% CI):<br>
                        <strong>Rs. ${res.Projected_Low.toLocaleString(undefined, {maximumFractionDigits: 0})} - Rs. ${res.Projected_High.toLocaleString(undefined, {maximumFractionDigits: 0})}</strong>
                    </div>
                </div>
            `;
        });
    }

    function renderStockIndicators() {
        const data = stockForecastData;
        const timeSeries = data.dates.map(d => new Date(d).getTime());
        
        // 1. RSI Chart
        const rsiOptions = {
            series: [{ name: 'RSI 14', data: data.rsi }],
            chart: { height: 200, type: 'line', background: 'transparent', toolbar: { show: false } },
            colors: ['#00C6FF'],
            xaxis: { type: 'datetime', labels: { show: false } },
            yaxis: { min: 0, max: 100, tickAmount: 4, labels: { style: { colors: '#94A3B8' } } },
            grid: { borderColor: '#1E293B' },
            theme: { mode: 'dark' },
            annotations: {
                yaxis: [
                    { y: 70, borderColor: '#FF4B4B', strokeDashArray: 3, label: { style: { color: '#fff', background: '#FF4B4B' }, text: 'Overbought (70)' } },
                    { y: 30, borderColor: '#00FF87', strokeDashArray: 3, label: { style: { color: '#fff', background: '#00FF87' }, text: 'Oversold (30)' } }
                ]
            }
        };
        if (rsiChart) rsiChart.destroy();
        rsiChart = new ApexCharts(document.getElementById("stock-chart-rsi"), rsiOptions);
        rsiChart.render();
        
        // 2. MACD Chart
        const macdOptions = {
            series: [
                { name: 'MACD', type: 'line', data: data.macd },
                { name: 'Signal Line', type: 'line', data: data.macd_sig },
                { name: 'MACD Histogram', type: 'bar', data: data.macd_hist }
            ],
            chart: { height: 200, background: 'transparent', toolbar: { show: false } },
            colors: ['#FFD700', '#8A9BB1', '#00C6FF'],
            stroke: { width: [1.5, 1.5, 0] },
            xaxis: { type: 'datetime', labels: { show: false } },
            yaxis: { labels: { style: { colors: '#94A3B8' } } },
            grid: { borderColor: '#1E293B' },
            theme: { mode: 'dark' }
        };
        if (macdChart) macdChart.destroy();
        macdChart = new ApexCharts(document.getElementById("stock-chart-macd"), macdOptions);
        macdChart.render();
        
        // 3. OBV Chart
        const obvOptions = {
            series: [{ name: 'OBV', data: data.obv }],
            chart: { height: 200, type: 'line', background: 'transparent', toolbar: { show: false } },
            colors: ['#8B5CF6'],
            xaxis: { type: 'datetime', labels: { show: false } },
            yaxis: { labels: { style: { colors: '#94A3B8' } } },
            grid: { borderColor: '#1E293B' },
            theme: { mode: 'dark' }
        };
        if (obvChart) obvChart.destroy();
        obvChart = new ApexCharts(document.getElementById("stock-chart-obv"), obvOptions);
        obvChart.render();
        
        // 4. ATR Chart
        const atrOptions = {
            series: [{ name: 'ATR 14', data: data.atr }],
            chart: { height: 200, type: 'line', background: 'transparent', toolbar: { show: false } },
            colors: ['#FF8C00'],
            xaxis: { type: 'datetime', labels: { show: false } },
            yaxis: { labels: { style: { colors: '#94A3B8' } } },
            grid: { borderColor: '#1E293B' },
            theme: { mode: 'dark' }
        };
        if (atrChart) atrChart.destroy();
        atrChart = new ApexCharts(document.getElementById("stock-chart-atr"), atrOptions);
        atrChart.render();
    }

    function renderStockTable() {
        const tableBody = document.querySelector("#stock-history-table tbody");
        tableBody.innerHTML = "";
        
        const data = stockForecastData;
        const length = data.dates.length;
        
        // Take last 7 days from dataset in reverse order
        for (let i = length - 1; i >= Math.max(0, length - 7); i--) {
            tableBody.innerHTML += `
                <tr>
                    <td style="font-weight: 700; color: #FFFFFF;">${data.dates[i]}</td>
                    <td>Rs. ${data.opens[i].toLocaleString(undefined, {minimumFractionDigits: 2})}</td>
                    <td>Rs. ${data.highs[i].toLocaleString(undefined, {minimumFractionDigits: 2})}</td>
                    <td>Rs. ${data.lows[i].toLocaleString(undefined, {minimumFractionDigits: 2})}</td>
                    <td style="font-weight: bold; color: #FFFFFF;">Rs. ${data.prices[i].toLocaleString(undefined, {minimumFractionDigits: 2})}</td>
                    <td>${data.volumes[i].toLocaleString()}</td>
                </tr>
            `;
        }
    }

    // ======================================================
    // 6. AI INVESTMENT ADVISOR PORTAL
    // ======================================================
    function initAdvisorPortal() {
        const calcRiskBtn = document.getElementById("calc-risk-btn");
        const genAdvisorBtn = document.getElementById("generate-advisor-btn");
        const surveyRadios = document.querySelectorAll('input[name="survey-risk"], input[name="survey-horizon"]');
        
        // Calculate risk tolerance from the quiz questionnaire
        calcRiskBtn.addEventListener("click", () => {
            const q1 = document.querySelector('input[name="quiz-q1"]:checked').value;
            const q2 = document.querySelector('input[name="quiz-q2"]:checked').value;
            const q3 = document.querySelector('input[name="quiz-q3"]:checked').value;
            
            let risk = "Medium";
            if (q1 === "low" || q2 === "low" || q3 === "low") {
                risk = "Low";
            } else if (q1 === "high" && q2 === "high" && q3 === "high") {
                risk = "High";
            }
            
            document.getElementById("advisor-risk").value = risk;
            alert(`Calculated risk suitability: ${risk}. Profile updated successfully!`);
            document.getElementById("quiz-collapse-content").parentElement.classList.remove("open");
        });
        
        // 2-Question quick-match survey listener
        surveyRadios.forEach(radio => {
            radio.addEventListener("change", () => {
                const isAggressive = document.querySelector('input[name="survey-risk"]:checked').value === "aggressive";
                const isLong = document.querySelector('input[name="survey-horizon"]:checked').value === "long";
                
                // Style highlight on option selections
                document.querySelectorAll(".survey-option").forEach(label => {
                    if (label.querySelector("input").checked) {
                        label.classList.add("active");
                    } else {
                        label.classList.remove("active");
                    }
                });
                
                let assetName = "";
                let assetDesc = "";
                
                if (!isAggressive) {
                    if (!isLong) {
                        assetName = "🟡 Gold (Commodity)";
                        assetDesc = "Gold is currently acting as a primary store of value and safe haven asset during current market volatilities, showing steady medium-term support bounds.";
                    } else {
                        assetName = "🚜 ENGRO.KA / HUBC.KA (PSX Stocks)";
                        assetDesc = "These blue-chip PSX stocks offer stable cash dividend yields and solid long-term value, making them excellent defensive holdings for conventional portfolios.";
                    }
                } else {
                    if (!isLong) {
                        assetName = "💻 SYS.KA (PSX Tech Stock)";
                        assetDesc = "Systems Limited (SYS.KA) has high daily volatility and is highly responsive to tech index sentiment, offering rapid short-term trading opportunities.";
                    } else {
                        assetName = "🏦 MEBL.KA (PSX Islamic Bank Stock)";
                        assetDesc = "Meezan Bank (MEBL.KA) is demonstrating strong long-term profit performance and acts as the premier Shariah-compliant high-growth equity benchmark.";
                    }
                }
                
                document.getElementById("survey-asset-name").innerText = assetName;
                document.getElementById("survey-asset-desc").innerText = assetDesc;
            });
        });
        
        // Generate AI Recommendations button click handler
        genAdvisorBtn.addEventListener("click", () => {
            const capital = parseFloat(document.getElementById("advisor-capital").value);
            const risk = document.getElementById("advisor-risk").value;
            const compliance = document.getElementById("advisor-compliance").value;
            
            genAdvisorBtn.innerText = "Querying suitability models... 💼";
            genAdvisorBtn.disabled = true;
            
            fetch("/api/recommend", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ capital, risk, compliance })
            })
            .then(res => res.json())
            .then(data => {
                const recs = data.recommendations;
                
                if (!recs || recs.length === 0) {
                    alert("No matching assets found for selected preferences.");
                    return;
                }
                
                // Show Advisor grid
                document.getElementById("advisor-results-grid").style.display = "grid";
                
                // Render recommendations list
                const listContainer = document.getElementById("advisor-allocation-list");
                listContainer.innerHTML = `<h4 style="color: #FFFFFF; font-size: 1.2rem; font-weight: 800; margin-bottom: 1rem;">Asset Allocations List</h4>`;
                
                recs.forEach((rec, idx) => {
                    const isIslamic = rec.is_islamic ? "Shariah Compliant 🟢" : "Conventional";
                    const reasonsHtml = rec.reasons.map(r => `<li>${r}</li>`).join("");
                    
                    listContainer.innerHTML += `
                        <div class="advisor-rec-card">
                            <div class="adv-rec-header">
                                <span class="adv-rec-title">Rank ${idx + 1}: ${rec.name}</span>
                                <span class="adv-rec-match">${rec.confidence_score}% Match</span>
                            </div>
                            <p class="adv-rec-meta">Category: <strong>${rec.category}</strong> | Type: <strong>${isIslamic}</strong></p>
                            <p class="adv-rec-cash">Allocated Cash Weight: <strong class="adv-rec-cash-val">Rs. ${rec.allocated_cash.toLocaleString(undefined, {minimumFractionDigits: 2})} (${(rec.weight * 100).toFixed(0)}%)</strong></p>
                            <div class="adv-rec-insight">
                                <p class="adv-rec-insight-title">Supporting AI Insights:</p>
                                <ul class="adv-rec-insight-list">${reasonsHtml}</ul>
                            </div>
                        </div>
                    `;
                });
                
                // Render allocations donut chart
                renderAdvisorDonut(recs);
            })
            .catch(err => {
                alert("Allocation generation failed: " + err.message);
            })
            .finally(() => {
                genAdvisorBtn.innerText = "Generate AI Portfolio Allocation 💼";
                genAdvisorBtn.disabled = false;
            });
        });
    }

    function fetchMacroIndicators() {
        fetch("/api/macro")
            .then(res => res.json())
            .then(data => {
                document.getElementById("macro-kse").innerText = data.kse100;
                document.getElementById("macro-kibor").innerText = data.kibor;
                document.getElementById("macro-inflation").innerText = data.inflation;
                document.getElementById("macro-exchange").innerText = data.usd_pkr;
            })
            .catch(err => console.error("Error loading macro indicators:", err));
    }

    function fetchMarketTrends() {
        const gainerTable = document.querySelector("#gainer-table tbody");
        const declinerTable = document.querySelector("#decliner-table tbody");
        const newsTable = document.querySelector("#trends-news-table tbody");
        
        gainerTable.innerHTML = "<tr><td colspan='4' style='text-align: center; color: #94A3B8;'>Querying AI models...</td></tr>";
        declinerTable.innerHTML = "<tr><td colspan='4' style='text-align: center; color: #94A3B8;'>Querying AI models...</td></tr>";
        newsTable.innerHTML = "<tr><td colspan='5' style='text-align: center; color: #94A3B8;'>Loading news sentiment signals...</td></tr>";
        
        fetch("/api/market-trends")
            .then(res => res.json())
            .then(data => {
                // 1. Populate Gainers Table
                gainerTable.innerHTML = "";
                data.gainers.forEach(g => {
                    gainerTable.innerHTML += `
                        <tr>
                            <td style="font-weight: 700; color: #FFFFFF;">${g.name}</td>
                            <td>${g.type}</td>
                            <td><span class="sentiment-badge bullish" style="padding: 2px 8px; font-size: 0.65rem;">${g.sentiment}</span></td>
                            <td style="font-weight: 800; color: ${g.color};">${g.return}</td>
                        </tr>
                    `;
                });
                
                // 2. Populate Decliners Table
                declinerTable.innerHTML = "";
                data.decliners.forEach(d => {
                    declinerTable.innerHTML += `
                        <tr>
                            <td style="font-weight: 700; color: #FFFFFF;">${d.name}</td>
                            <td>${d.type}</td>
                            <td><span class="sentiment-badge bearish" style="padding: 2px 8px; font-size: 0.65rem;">${d.sentiment}</span></td>
                            <td style="font-weight: 800; color: ${d.color};">${d.return}</td>
                        </tr>
                    `;
                });
                
                // 3. Populate News Table
                newsTable.innerHTML = "";
                data.news.forEach(n => {
                    const badgeClass = n.sentiment.toLowerCase();
                    newsTable.innerHTML += `
                        <tr>
                            <td style="font-weight: 600; color: #E2E8F0; line-height: 1.4;">${n.title}</td>
                            <td><span class="sentiment-badge ${badgeClass}" style="padding: 3px 10px; font-size: 0.65rem;">${n.sentiment}</span></td>
                            <td style="font-weight: 700; color: ${n.color};">${n.score}</td>
                            <td><span style="font-weight: bold; color: ${n.importance === 'High' ? '#FF4B4B' : n.importance === 'Medium' ? '#FFA500' : '#00FF87'}">${n.importance}</span></td>
                            <td style="color: #64748B; font-size: 0.75rem;">${n.date}</td>
                        </tr>
                    `;
                });
            })
            .catch(err => {
                gainerTable.innerHTML = "<tr><td colspan='4' style='color: #FF4B4B;'>Failed to load market trends.</td></tr>";
                declinerTable.innerHTML = "<tr><td colspan='4' style='color: #FF4B4B;'>Failed to load market trends.</td></tr>";
                newsTable.innerHTML = "<tr><td colspan='5' style='color: #FF4B4B;'>Failed to load news.</td></tr>";
                console.error("Error loading market trends:", err);
            });
            
        // Query Ingestion & scraping stats
        fetch("/api/pipeline/status")
            .then(res => res.json())
            .then(data => {
                document.getElementById("pipeline-status").innerText = data.status;
                document.getElementById("pipeline-count").innerText = data.total_articles.toLocaleString();
                document.getElementById("pipeline-last-run").innerText = data.last_run;
                document.getElementById("sent-pos").innerText = `${data.positive_pct}%`;
                document.getElementById("sent-neut").innerText = `${data.neutral_pct}%`;
                document.getElementById("sent-neg").innerText = `${data.negative_pct}%`;
            })
            .catch(err => console.error("Error loading pipeline status:", err));
    }

    function initPipelineControl() {
        const scraperBtn = document.getElementById("run-scraper-btn");
        if (scraperBtn) {
            scraperBtn.addEventListener("click", () => {
                scraperBtn.innerText = "Syncing Feeds... ⚡";
                scraperBtn.disabled = true;
                
                fetch("/api/pipeline/trigger", { method: "POST" })
                    .then(res => res.json())
                    .then(data => {
                        alert(data.message);
                        fetchMarketTrends(); // Refresh trends lists and stats!
                    })
                    .catch(err => {
                        alert("Scraper Pipeline sync failed: " + err.message);
                    })
                    .finally(() => {
                        scraperBtn.innerText = "Run Scraper Pipeline ⚡";
                        scraperBtn.disabled = false;
                    });
            });
        }
    }

    function renderAdvisorDonut(recs) {
        const chartElement = document.getElementById("advisor-donut-chart");
        chartElement.innerHTML = "";
        
        const labels = recs.map(r => r.name);
        const values = recs.map(r => r.allocated_cash);
        
        const options = {
            series: values,
            chart: {
                height: 350,
                type: 'donut',
                background: 'transparent'
            },
            labels: labels,
            colors: ['#10B981', '#00C6FF', '#FFD700'],
            theme: { mode: 'dark' },
            legend: {
                position: 'bottom',
                labels: { colors: '#94A3B8' }
            },
            dataLabels: {
                enabled: true,
                formatter: (val, opts) => `${val.toFixed(0)}%`
            },
            plotOptions: {
                pie: {
                    donut: {
                        size: '60%',
                        background: 'transparent'
                    }
                }
            }
        };
        
        if (advisorDonutChart) {
            advisorDonutChart.destroy();
        }
        advisorDonutChart = new ApexCharts(chartElement, options);
        advisorDonutChart.render();
    }
});
