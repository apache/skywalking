package test.a.eye.cloud.bytebuddy;

public class TestClass {
	public TestClass(){
		//System.out.println("init:" + this.getClass().getName());
	}
	
	public TestClass(String tmp){
		//System.out.println("init:" + this.getClass().getName());
	}
	
	
	public String testA(String aa){
//		throw new RuntimeException("adfasdfas");
		return "TestClass.testA";
	}
}
