<#import "./marco/common.ftl" as common>
<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8">
    <title>jQuery treetable</title>
    <link rel="stylesheet" href="css/jquery.treetable.css" />
    <link rel="stylesheet" href="css/jquery.treetable.theme.default.css" />
	<link rel="stylesheet" href="css/bootstrap.css" />
	<link rel="stylesheet" href="css/traceLog.css" />
	
  </head>
  <body>
    <div id="row">
		<div class="col-md-12">
			<table id="example-advanced">
				<caption>
				  <a href="#" onclick="jQuery('#example-advanced').treetable('expandAll'); return false;">Expand all</a>
				  &nbsp;
				  <a href="#" onclick="jQuery('#example-advanced').treetable('collapseAll'); return false;">Collapse all</a>
				</caption>
				<thead>
				  <tr>
					<th class="col-md-3">应用名</th>
					<th class="col-md-1">类型</th>
					<th class="col-md-1">状态</th>
					<th class="col-md-1">大小</th>
					<th class="col-md-3">服务/方法</th>
					<th class="col-md-3">时间轴</th>
				  </tr>
				</thead>
				<tbody>
					  <tr data-tt-id='1'>
							<td>
								<b>tf_buy</b>
							</td>
							<td>
								TRACE
							</td>
							<td>
								OK
							</td>
							<td>
								-
							</td>
							<td>
								http://buy.taobao.com/auction/buy_now.html
							</td>
							<td>
								<div class="progress">
									<div class="progress-bar progress-bar-success progress-bar-striped" style="width:50%">
										239ms
									</div>
								</div>
							</td>
					  </tr>
			   
					  <tr data-tt-id='1-1' data-tt-parent-id='1'>
							<td>
								<b>tradeplatform</b>
							</td>
							<td>
								HSF
							</td>
							<td>
								OK
							</td>
							<td>
								670B
							</td>
							<td>
								tc.TcTradeService@getOutOrderSwqIdByBuyerId~1
							</td>
							<td>
								<div class="progress">
									<div class="progress-bar" style="width: 2%">
											
									</div>
									<div class="progress-bar  progress-bar-success progress-bar-striped" style="min-width: 2%;">
											0ms
									</div>
									
								</div>
							</td>
					  </tr>
					  <tr data-tt-id='1-2' data-tt-parent-id='1'>
						<td>
							<b>tradeplatform</b>
						</td>
						<td>
							HSF
						</td>
						<td>
							OK
						</td>
						<td>
							8.0KB
						</td>
						<td>
							trade.ICreatingOrderService@createOrdersForTaobao~R
						</td>
						<td>
								<div class="progress">
									<div class="progress-bar" style="width: 2%">
											
									</div>
									<div class="progress-bar  progress-bar-warning progress-bar-striped" style="min-width: 2%;">
											
									</div>
									<div class="progress-bar  progress-bar-success progress-bar-striped" style="min-width: 8%;">
											46ms
									</div>
									<div class="progress-bar  progress-bar-info progress-bar-striped" style="min-width: 2%;">
											
									</div>
								</div>
						</td>
					  </tr>
					  <tr data-tt-id='1-2-1' data-tt-parent-id='1-2'>
						<td>
							<b>Itemcenter</b>
						</td>
						<td>
							HSF
						</td>
						<td>
							OK
						</td>
						<td>
							5.0KB
						</td>
						<td>
							item.IItermQueryService@queryItermAndSkWithPVToText~L
						</td>
						<td>
								<div class="progress">
									<div class="progress-bar" style="width: 12%">
											
									</div>
									
									<div class="progress-bar  progress-bar-success progress-bar-striped" style="min-width: 5%;">
											8ms
									</div>
									
								</div>
						</td>
					  </tr>
					  <tr data-tt-id='1-2-2' data-tt-parent-id='1-2'>
						<td>
							<b>Itemcenter</b>
						</td>
						<td>
							HSF
						</td>
						<td>
							OK
						</td>
						<td>
							3.0KB
						</td>
						<td>
							item.SpuService@getSpu~l
						</td>
						<td>
								<div class="progress">
									<div class="progress-bar" style="width: 17%">
											
									</div>
									
									<div class="progress-bar  progress-bar-success progress-bar-striped" style="min-width: 2%;">
											3ms
									</div>
									
								</div>
						</td>
					  </tr>
					  <tr data-tt-id='2'>
						<td>
							<b>misccenter</b>
						</td>
						<td>
							HSF
						</td>
						<td>
							OK
						</td>
						<td>
							3.0KB
						</td>
						<td>
							misccenter.EcardOrderService@insertEcardOrder~E
						</td>
						<td>
							<div class="progress">
									<div class="progress-bar" style="width: 19%">
											
									</div>
									
									<div class="progress-bar  progress-bar-success progress-bar-striped" style="min-width: 4%;">
											6ms
									</div>
									
							</div>
						</td>
					  </tr>
				</tbody>
			</table>
		</div>
    </div>

    <!-- script references -->
	<@common.importJavaScript />
    <script>
		var table = $('#example-advanced').children();  
		$("#example-advanced").treetable({ expandable: true });
		
		$("#example-advanced tr").click(function() {
			var selected = $(this).hasClass("highlight");
			$("#example-advanced tr").removeClass("highlight");
			if(!selected)
            $(this).addClass("highlight");
		});
		
    </script>
  </body>
</html>
