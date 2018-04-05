using Android.App;
using Android.Widget;
using Android.OS;

namespace BSN.Resa.NetworkQuality.Droid.Manual.Tests
{
	[Activity(Label = "BSN.Resa.NetworkQuality.Droid.Manual.Tests", MainLauncher = true)]
	public class MainActivity : Activity
	{
		protected override void OnCreate(Bundle savedInstanceState)
		{
			base.OnCreate(savedInstanceState);

			// Set our view from the "main" layout resource
			SetContentView(Resource.Layout.Main);
		}
	}
}

