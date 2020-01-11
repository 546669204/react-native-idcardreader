
import { NativeModules,NativeEventEmitter } from 'react-native';

const { RNIDCardReader3xx } = NativeModules;

export default {
	search:function(){
		RNIDCardReader3xx.search();
	},
  connect:function(mac){
		return RNIDCardReader3xx.connect(mac);
  },
  close:function(){
		RNIDCardReader3xx.close();
	},
	start:function(){
		RNIDCardReader3xx.start();
	},
	stop:function(){
		RNIDCardReader3xx.stop();
	},
	on:function(fn){
		const eventEmitter = new NativeEventEmitter(RNIDCardReader3xx);
		eventEmitter.addListener('callback', (event) => {
		   fn(event)
		});
  },
  offAll:function(){
    eventEmitter.removeAllListeners('callback');
  }
};
